use super::{Ctx, Outbound, PeerInfo, Session, Status};
use crate::events::{Direction, SessionEvent};
use async_trait::async_trait;
use futures_util::{SinkExt, StreamExt};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::sync::mpsc;
use tokio_tungstenite::tungstenite::client::IntoClientRequest;
use tokio_tungstenite::tungstenite::Message;
use tokio_util::sync::CancellationToken;

pub struct WsClient {
    ctx: Arc<Ctx>,
    tx: mpsc::UnboundedSender<Outbound>,
    rx: Mutex<Option<mpsc::UnboundedReceiver<Outbound>>>,
    state: Arc<Mutex<State>>,
}

#[derive(Default)]
struct State {
    online: bool,
    reconnecting: bool,
    local: Option<String>,
    since: i64,
}

pub fn ws_url(cfg: &crate::config::SessionConfig) -> String {
    let host = cfg.host.trim();
    if host.starts_with("ws://") || host.starts_with("wss://") {
        return host.to_string();
    }
    let path = if cfg.ws_path.starts_with('/') { cfg.ws_path.clone() } else { format!("/{}", cfg.ws_path) };
    let scheme = if cfg.port == 443 { "wss" } else { "ws" };
    let host = if host.contains(':') && !host.starts_with('[') { format!("[{host}]") } else { host.to_string() };
    format!("{scheme}://{host}:{}{path}", cfg.port)
}

impl WsClient {
    pub fn new(ctx: Arc<Ctx>) -> Self {
        let (tx, rx) = mpsc::unbounded_channel();
        Self { ctx, tx, rx: Mutex::new(Some(rx)), state: Arc::default() }
    }

    async fn connect_once(ctx: &Ctx) -> Result<tokio_tungstenite::WebSocketStream<tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>>, String> {
        let url = ws_url(&ctx.cfg);
        let mut req = url.clone().into_client_request().map_err(|e| format!("地址无效 {url}: {e}"))?;
        for (k, v) in &ctx.cfg.ws_headers {
            if k.trim().is_empty() { continue; }
            let name = tokio_tungstenite::tungstenite::http::HeaderName::from_bytes(k.trim().as_bytes()).map_err(|e| format!("请求头名称无效 {k}: {e}"))?;
            let value = tokio_tungstenite::tungstenite::http::HeaderValue::from_str(v.trim()).map_err(|e| format!("请求头值无效 {k}: {e}"))?;
            req.headers_mut().insert(name, value);
        }
        let timeout = Duration::from_millis(ctx.cfg.connect_timeout_ms.max(100) as u64);
        let (stream, _resp) = tokio::time::timeout(timeout, tokio_tungstenite::connect_async(req))
            .await
            .map_err(|_| format!("连接超时（{} ms）", timeout.as_millis()))?
            .map_err(|e| format!("连接失败: {e}"))?;
        Ok(stream)
    }

    async fn serve(ctx: &Arc<Ctx>, stream: tokio_tungstenite::WebSocketStream<tokio_tungstenite::MaybeTlsStream<tokio::net::TcpStream>>, rx: &mut mpsc::UnboundedReceiver<Outbound>, state: &Arc<Mutex<State>>, tx: &mpsc::UnboundedSender<Outbound>) {
        let local = match stream.get_ref() {
            tokio_tungstenite::MaybeTlsStream::Plain(s) => s.local_addr().ok().map(|a| a.to_string()),
            _ => None,
        };
        // Publish `online` inside the state lock so it serializes with stop()'s `offline`
        // (also emitted under this lock). A cancel flag check alone leaves a window between
        // unlock and emit where stop's offline could precede a late online, stranding the UI.
        {
            let mut s = state.lock().unwrap();
            if ctx.cancel.is_cancelled() {
                return;
            }
            s.online = true;
            s.reconnecting = false;
            s.local = local.clone();
            s.since = crate::events::now_ms();
            ctx.online(local);
        }
        let (mut sink, mut source) = stream.split();
        let timed_stop = CancellationToken::new();
        ctx.spawn_timed_send(tx.clone(), timed_stop.clone());
        let reason = loop {
            tokio::select! {
                _ = ctx.cancel.cancelled() => break None,
                msg = source.next() => match msg {
                    Some(Ok(Message::Text(t))) => ctx.inbound(None, t.as_bytes().to_vec(), tx.clone()),
                    Some(Ok(Message::Binary(b))) => ctx.inbound(None, b.to_vec(), tx.clone()),
                    Some(Ok(Message::Ping(p))) => { let _ = sink.send(Message::Pong(p)).await; }
                    Some(Ok(Message::Close(f))) => break Some(f.map(|f| format!("对端关闭: {} {}", f.code, f.reason)).unwrap_or_else(|| "对端关闭连接".into())),
                    Some(Ok(_)) => {}
                    Some(Err(e)) => break Some(format!("读取失败: {e}")),
                    None => break Some("连接已断开".into()),
                },
                out = rx.recv() => match out {
                    Some(o) => {
                        if !o.bytes.is_empty() {
                            let m = if o.as_text { match String::from_utf8(o.bytes.clone()) { Ok(s) => Message::Text(s.into()), Err(_) => Message::Binary(o.bytes.clone().into()) } } else { Message::Binary(o.bytes.clone().into()) };
                            if let Err(e) = sink.send(m).await { break Some(format!("发送失败: {e}")); }
                            ctx.message(None, Direction::Out, &o.bytes);
                        }
                        if o.disconnect { let _ = sink.send(Message::Close(None)).await; break Some("按规则主动断开".into()); }
                    }
                    None => break None,
                },
            }
        };
        timed_stop.cancel();
        let _ = sink.close().await;
        // Whoever clears `online` owns the offline notification, decided and emitted under the lock,
        // so a concurrent stop cannot double-notify or drop the event and strand the UI connected.
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
            if ctx.cancel.is_cancelled() { return; }
            if ctx.cfg.reconnect_max > 0 && attempt > ctx.cfg.reconnect_max {
                state.lock().unwrap().reconnecting = false;
                ctx.error(format!("重连 {} 次均失败，已停止", ctx.cfg.reconnect_max));
                ctx.offline(Some(format!("重连 {} 次均失败", ctx.cfg.reconnect_max)));
                return;
            }
            // Restore the flag on every retry round (serve() clears it on a successful reconnect).
            state.lock().unwrap().reconnecting = true;
            tokio::select! { _ = tokio::time::sleep(interval) => {}, _ = ctx.cancel.cancelled() => return }
            ctx.emit(SessionEvent::Reconnecting { uid: ctx.uid().to_string(), attempt });
            let connected = tokio::select! {
                r = Self::connect_once(&ctx) => r,
                _ = ctx.cancel.cancelled() => return,
            };
            match connected {
                Ok(stream) => {
                    Self::serve(&ctx, stream, &mut rx, &state, &tx).await;
                    if ctx.cancel.is_cancelled() { return; }
                    attempt = 1;
                }
                Err(e) => { ctx.error(e); attempt += 1; }
            }
        }
    }
}

#[async_trait]
impl Session for WsClient {
    async fn start(&self) -> Result<(), String> {
        let mut rx = self.rx.lock().unwrap().take().ok_or("会话已启动")?;
        let ctx = self.ctx.clone();
        let state = self.state.clone();
        let tx = self.tx.clone();
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
            self.ctx.offline(Some("手动断开".into()));
        }
    }

    async fn send(&self, out: Outbound) -> Result<(), String> {
        if !self.state.lock().unwrap().online { return Err("未连接".into()); }
        self.tx.send(out).map_err(|_| "会话已关闭".to_string())
    }

    async fn status(&self) -> Status {
        let s = self.state.lock().unwrap();
        Status { online: s.online, local: s.local.clone(), peers: if s.online { vec![PeerInfo { addr: ws_url(&self.ctx.cfg), online: true, since: s.since }] } else { vec![] } }
    }
}
