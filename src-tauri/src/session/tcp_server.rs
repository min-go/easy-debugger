use super::{apply_tcp_opts, Ctx, Outbound, PeerInfo, Session, Status};
use crate::config::FramingMode;
use crate::events::{Direction, SessionEvent};
use crate::framing::Framer;
use async_trait::async_trait;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

/// Per-peer bookkeeping shared by TCP and WebSocket servers.
pub struct Peer {
    pub tx: mpsc::UnboundedSender<Outbound>,
    pub cancel: CancellationToken,
    pub since: i64,
    pub online: bool,
}

#[derive(Default)]
pub struct ServerState {
    pub online: bool,
    pub local: Option<String>,
    pub peers: HashMap<String, Peer>,
    /// Insertion order for stable display.
    pub order: Vec<String>,
}

impl ServerState {
    pub fn add_peer(&mut self, addr: String, tx: mpsc::UnboundedSender<Outbound>, cancel: CancellationToken) {
        if !self.order.contains(&addr) {
            self.order.push(addr.clone());
        }
        self.peers.insert(addr, Peer { tx, cancel, since: crate::events::now_ms(), online: true });
    }

    pub fn mark_offline(&mut self, addr: &str) {
        if let Some(p) = self.peers.get_mut(addr) {
            p.online = false;
        }
    }

    pub fn online_count(&self) -> usize {
        self.peers.values().filter(|p| p.online).count()
    }

    pub fn status(&self) -> Status {
        Status {
            online: self.online,
            local: self.local.clone(),
            peers: self.order.iter().filter_map(|a| self.peers.get(a).map(|p| PeerInfo { addr: a.clone(), online: p.online, since: p.since })).collect(),
        }
    }

    pub fn route(&self, out: Outbound) -> Result<(), String> {
        match &out.target {
            Some(addr) => {
                let p = self.peers.get(addr).filter(|p| p.online).ok_or_else(|| format!("对端 {addr} 不在线"))?;
                p.tx.send(out.clone()).map_err(|_| "对端已断开".to_string())
            }
            None => {
                let mut n = 0;
                for p in self.peers.values().filter(|p| p.online) {
                    if p.tx.send(out.clone()).is_ok() {
                        n += 1;
                    }
                }
                if n == 0 { Err("没有在线的对端".into()) } else { Ok(()) }
            }
        }
    }
}

pub struct TcpServer {
    ctx: Arc<Ctx>,
    state: Arc<Mutex<ServerState>>,
}

impl TcpServer {
    pub fn new(ctx: Arc<Ctx>) -> Self {
        Self { ctx, state: Arc::default() }
    }

    async fn handle_peer(ctx: Arc<Ctx>, stream: TcpStream, addr: String, state: Arc<Mutex<ServerState>>, mut rx: mpsc::UnboundedReceiver<Outbound>, tx: mpsc::UnboundedSender<Outbound>, cancel: CancellationToken) {
        apply_tcp_opts(&stream, &ctx.cfg);
        let (mut rd, mut wr) = stream.into_split();
        let mut framer = Framer::new(&ctx.cfg.framing);
        let mut buf = vec![0u8; 64 * 1024];
        let peer = Some(addr.clone());
        let reason = loop {
            let flush_after = if framer.mode() == FramingMode::Timeout && framer.has_pending() { Some(Duration::from_millis(framer.timeout_ms())) } else { None };
            tokio::select! {
                _ = ctx.cancel.cancelled() => break None,
                _ = cancel.cancelled() => break Some("被服务端踢出".into()),
                r = rd.read(&mut buf) => match r {
                    Ok(0) => break Some("对端关闭连接".into()),
                    Ok(n) => for frame in framer.feed(&buf[..n]) { ctx.inbound(peer.clone(), frame, tx.clone()); },
                    Err(e) => break Some(format!("读取失败: {e}")),
                },
                _ = tokio::time::sleep(flush_after.unwrap_or(Duration::MAX)), if flush_after.is_some() => {
                    if let Some(f) = framer.flush() { ctx.inbound(peer.clone(), f, tx.clone()); }
                }
                out = rx.recv() => match out {
                    Some(o) => {
                        if !o.bytes.is_empty() {
                            if let Err(e) = wr.write_all(&o.bytes).await { break Some(format!("发送失败: {e}")); }
                            ctx.message(peer.clone(), Direction::Out, &o.bytes);
                        }
                        if o.disconnect { break Some("按规则主动断开".into()); }
                    }
                    None => break None,
                },
            }
        };
        if let Some(f) = framer.flush() {
            ctx.inbound(peer.clone(), f, tx.clone());
        }
        state.lock().unwrap().mark_offline(&addr);
        if !ctx.cancel.is_cancelled() {
            ctx.emit(SessionEvent::PeerOffline { uid: ctx.uid().to_string(), peer: addr, reason });
        }
    }
}

#[async_trait]
impl Session for TcpServer {
    async fn start(&self) -> Result<(), String> {
        let cfg = &self.ctx.cfg;
        let addr = super::resolve_addr(&cfg.host, cfg.port).await?;
        let listener = TcpListener::bind(addr).await.map_err(|e| format!("监听失败 {addr}: {e}"))?;
        let local = listener.local_addr().ok().map(|a| a.to_string());
        {
            let mut s = self.state.lock().unwrap();
            s.online = true;
            s.local = local.clone();
        }
        self.ctx.online(local);
        let ctx = self.ctx.clone();
        let state = self.state.clone();
        tokio::spawn(async move {
            loop {
                tokio::select! {
                    _ = ctx.cancel.cancelled() => break,
                    r = listener.accept() => match r {
                        Ok((stream, peer)) => {
                            let addr = peer.to_string();
                            let max = ctx.cfg.max_connections as usize;
                            if max > 0 && state.lock().unwrap().online_count() >= max {
                                ctx.info(format!("{addr} 超过最大连接数，已拒绝"));
                                drop(stream);
                                continue;
                            }
                            let (tx, rx) = mpsc::unbounded_channel();
                            let cancel = CancellationToken::new();
                            state.lock().unwrap().add_peer(addr.clone(), tx.clone(), cancel.clone());
                            ctx.emit(SessionEvent::PeerOnline { uid: ctx.uid().to_string(), peer: addr.clone() });
                            tokio::spawn(Self::handle_peer(ctx.clone(), stream, addr, state.clone(), rx, tx, cancel));
                        }
                        Err(e) => { ctx.error(format!("accept 失败: {e}")); tokio::time::sleep(Duration::from_millis(200)).await; }
                    }
                }
            }
        });
        Ok(())
    }

    async fn stop(&self) {
        self.ctx.cancel.cancel();
        let was_online = {
            let mut s = self.state.lock().unwrap();
            let w = s.online;
            s.online = false;
            for p in s.peers.values_mut() {
                p.online = false;
            }
            w
        };
        if was_online {
            self.ctx.offline(Some("已关闭监听".into()));
        }
    }

    async fn send(&self, out: Outbound) -> Result<(), String> {
        self.state.lock().unwrap().route(out)
    }

    async fn status(&self) -> Status {
        self.state.lock().unwrap().status()
    }

    async fn kick(&self, peer: &str) -> Result<(), String> {
        let s = self.state.lock().unwrap();
        let p = s.peers.get(peer).filter(|p| p.online).ok_or("对端不在线")?;
        p.cancel.cancel();
        Ok(())
    }
}
