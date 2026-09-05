use super::{apply_tcp_opts, resolve_addr, Ctx, Outbound, PeerInfo, Session, Status};
use crate::events::{Direction, SessionEvent};
use crate::framing::Framer;
use crate::config::FramingMode;
use async_trait::async_trait;
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpSocket, TcpStream};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

pub struct TcpClient {
    ctx: Arc<Ctx>,
    tx: mpsc::UnboundedSender<Outbound>,
    rx: Mutex<Option<mpsc::UnboundedReceiver<Outbound>>>,
    state: Arc<Mutex<State>>,
}

#[derive(Default)]
struct State {
    online: bool,
    /// True while the reconnect loop is active (channel shown as "reconnecting" in the UI).
    reconnecting: bool,
    local: Option<String>,
    peer: Option<String>,
    since: i64,
}

impl TcpClient {
    pub fn new(ctx: Arc<Ctx>) -> Self {
        let (tx, rx) = mpsc::unbounded_channel();
        Self { ctx, tx, rx: Mutex::new(Some(rx)), state: Arc::default() }
    }

    async fn connect_once(ctx: &Ctx) -> Result<TcpStream, String> {
        let cfg = &ctx.cfg;
        let addr = resolve_addr(&cfg.host, cfg.port).await?;
        let socket = if addr.is_ipv4() { TcpSocket::new_v4() } else { TcpSocket::new_v6() }.map_err(|e| e.to_string())?;
        if !cfg.local_bind.trim().is_empty() {
            let bind: std::net::SocketAddr = cfg.local_bind.parse().map_err(|_| "本地绑定地址格式应为 ip:port".to_string())?;
            socket.set_reuseaddr(true).ok();
            socket.bind(bind).map_err(|e| format!("本地绑定失败: {e}"))?;
        }
        let _ = socket.set_keepalive(cfg.keepalive);
        let timeout = Duration::from_millis(cfg.connect_timeout_ms.max(100) as u64);
        let stream = tokio::time::timeout(timeout, socket.connect(addr))
            .await
            .map_err(|_| format!("连接超时（{} ms）", timeout.as_millis()))?
            .map_err(|e| format!("连接失败: {e}"))?;
        apply_tcp_opts(&stream, cfg);
        Ok(stream)
    }

    /// Drive one connection until it drops. Returns the disconnect reason.
    async fn run_connection(ctx: &Arc<Ctx>, stream: TcpStream, rx: &mut mpsc::UnboundedReceiver<Outbound>, tx: &mpsc::UnboundedSender<Outbound>) -> Option<String> {
        let (mut rd, mut wr) = stream.into_split();
        let mut framer = Framer::new(&ctx.cfg.framing);
        let mut buf = vec![0u8; 64 * 1024];
        let timed_stop = CancellationToken::new();
        ctx.spawn_timed_send(tx.clone(), timed_stop.clone());
        let reason = loop {
            let flush_after = if framer.mode() == FramingMode::Timeout && framer.has_pending() {
                Some(Duration::from_millis(framer.timeout_ms()))
            } else {
                None
            };
            tokio::select! {
                _ = ctx.cancel.cancelled() => break None,
                r = rd.read(&mut buf) => match r {
                    Ok(0) => break Some("对端关闭连接".into()),
                    Ok(n) => {
                        for frame in framer.feed(&buf[..n]) {
                            ctx.inbound(None, frame, tx.clone());
                        }
                    }
                    Err(e) => break Some(format!("读取失败: {e}")),
                },
                _ = tokio::time::sleep(flush_after.unwrap_or(Duration::MAX)), if flush_after.is_some() => {
                    if let Some(f) = framer.flush() { ctx.inbound(None, f, tx.clone()); }
                }
                out = rx.recv() => match out {
                    Some(o) => {
                        if !o.bytes.is_empty() {
                            if let Err(e) = wr.write_all(&o.bytes).await {
                                break Some(format!("发送失败: {e}"));
                            }
                            ctx.message(None, Direction::Out, &o.bytes);
                        }
                        if o.disconnect { break Some("按规则主动断开".into()); }
                    }
                    None => break None,
                },
            }
        };
        timed_stop.cancel();
        if let Some(f) = framer.flush() {
            ctx.inbound(None, f, tx.clone());
        }
        reason
    }

    async fn serve(ctx: &Arc<Ctx>, stream: TcpStream, rx: &mut mpsc::UnboundedReceiver<Outbound>, state: &Arc<Mutex<State>>, tx: &mpsc::UnboundedSender<Outbound>) {
        let local = stream.local_addr().ok().map(|a| a.to_string());
        let peer = stream.peer_addr().ok().map(|a| a.to_string());
        // Publish `online` *inside* the state lock so it serializes with stop()'s `offline`
        // (which also emits under this lock). Checking the cancel flag alone is not enough:
        // stop could slip between unlock and emit, sending offline first and leaving a late
        // online to strand the UI as connected. stop() cancels before locking, so a cancelled
        // token seen here means offline was already sent — skip online entirely.
        {
            let mut s = state.lock().unwrap();
            if ctx.cancel.is_cancelled() {
                return;
            }
            s.online = true;
            s.reconnecting = false;
            s.local = local.clone();
            s.peer = peer;
            s.since = crate::events::now_ms();
            ctx.online(local);
        }
        let reason = Self::run_connection(ctx, stream, rx, tx).await;
        // Whoever clears `online` (this cleanup or stop) owns the offline notification, decided and
        // emitted under the same lock, so a concurrent stop can neither double-notify nor let the
        // event be dropped (which would strand the UI connected).
        let mut s = state.lock().unwrap();
        if s.online {
            s.online = false;
            let reason = if ctx.cancel.is_cancelled() { Some("手动断开".into()) } else { reason };
            ctx.offline(reason);
        }
    }

    async fn reconnect_loop(ctx: Arc<Ctx>, mut rx: mpsc::UnboundedReceiver<Outbound>, state: Arc<Mutex<State>>, tx: mpsc::UnboundedSender<Outbound>, mut attempt: u32) {
        let interval = Duration::from_millis(ctx.cfg.reconnect_interval_ms.max(200) as u64);
        loop {
            if ctx.cancel.is_cancelled() {
                return;
            }
            if ctx.cfg.reconnect_max > 0 && attempt > ctx.cfg.reconnect_max {
                state.lock().unwrap().reconnecting = false;
                ctx.error(format!("重连 {} 次均失败，已停止", ctx.cfg.reconnect_max));
                ctx.offline(Some(format!("重连 {} 次均失败", ctx.cfg.reconnect_max)));
                return;
            }
            // Restore the flag on every retry round: a successful reconnect clears it in serve(),
            // and a later drop re-enters this loop and must mark the session reconnecting again.
            state.lock().unwrap().reconnecting = true;
            tokio::select! {
                _ = tokio::time::sleep(interval) => {}
                _ = ctx.cancel.cancelled() => return,
            }
            ctx.emit(SessionEvent::Reconnecting { uid: ctx.uid().to_string(), attempt });
            let connected = tokio::select! {
                r = Self::connect_once(&ctx) => r,
                _ = ctx.cancel.cancelled() => return,
            };
            match connected {
                Ok(stream) => {
                    Self::serve(&ctx, stream, &mut rx, &state, &tx).await;
                    if ctx.cancel.is_cancelled() {
                        return;
                    }
                    attempt = 1;
                }
                Err(e) => {
                    ctx.error(e);
                    attempt += 1;
                }
            }
        }
    }
}

#[async_trait]
impl Session for TcpClient {
    async fn start(&self) -> Result<(), String> {
        let mut rx = self.rx.lock().unwrap().take().ok_or("会话已启动")?;
        let ctx = self.ctx.clone();
        let state = self.state.clone();
        let tx = self.tx.clone();
        // First attempt is awaited so the caller sees an immediate failure.
        let stream = match Self::connect_once(&ctx).await {
            Ok(s) => s,
            Err(e) if ctx.cfg.auto_reconnect => {
                ctx.error(e);
                tokio::spawn(async move { Self::reconnect_loop(ctx, rx, state, tx, 1).await });
                return Ok(());
            }
            Err(e) => return Err(e),
        };
        tokio::spawn(async move {
            Self::serve(&ctx, stream, &mut rx, &state, &tx).await;
            if ctx.cfg.auto_reconnect && !ctx.cancel.is_cancelled() {
                Self::reconnect_loop(ctx, rx, state, tx, 1).await;
            }
        });
        Ok(())
    }

    async fn stop(&self) {
        self.ctx.cancel.cancel();
        let mut s = self.state.lock().unwrap();
        if s.online || s.reconnecting {
            s.online = false;
            s.reconnecting = false;
            // Emit under the lock so serve()'s online and this offline never interleave.
            self.ctx.offline(Some("手动断开".into()));
        }
    }

    async fn send(&self, out: Outbound) -> Result<(), String> {
        if !self.state.lock().unwrap().online {
            return Err("未连接".into());
        }
        self.tx.send(out).map_err(|_| "会话已关闭".to_string())
    }

    async fn status(&self) -> Status {
        let s = self.state.lock().unwrap();
        Status {
            online: s.online,
            local: s.local.clone(),
            peers: s.peer.iter().map(|p| PeerInfo { addr: p.clone(), online: s.online, since: s.since }).collect(),
        }
    }
}
