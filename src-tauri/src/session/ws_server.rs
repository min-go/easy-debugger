use super::tcp_server::ServerState;
use super::{apply_tcp_opts, Ctx, Outbound, Session, Status};
use crate::events::{Direction, SessionEvent};
use async_trait::async_trait;
use futures_util::{SinkExt, StreamExt};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio_tungstenite::tungstenite::Message;
use tokio_util::sync::CancellationToken;

pub struct WsServer {
    ctx: Arc<Ctx>,
    state: Arc<Mutex<ServerState>>,
}

impl WsServer {
    pub fn new(ctx: Arc<Ctx>) -> Self {
        Self { ctx, state: Arc::default() }
    }

    async fn handle_peer(ctx: Arc<Ctx>, stream: TcpStream, addr: String, state: Arc<Mutex<ServerState>>, mut rx: mpsc::UnboundedReceiver<Outbound>, tx: mpsc::UnboundedSender<Outbound>, cancel: CancellationToken) {
        apply_tcp_opts(&stream, &ctx.cfg);
        let expected_path = ctx.cfg.ws_path.trim().to_string();
        let path_seen = Arc::new(Mutex::new(String::new()));
        let ps = path_seen.clone();
        let handshake = tokio_tungstenite::accept_hdr_async(stream, move |req: &tokio_tungstenite::tungstenite::handshake::server::Request, resp| {
            *ps.lock().unwrap() = req.uri().path().to_string();
            Ok(resp)
        });
        // A peer that opens a TCP connection but never completes the WebSocket handshake must not
        // hold an online slot forever: bound it by a timeout, and honor stop / kick while waiting.
        let ws = tokio::select! {
            r = tokio::time::timeout(std::time::Duration::from_secs(10), handshake) => match r {
                Ok(Ok(w)) => w,
                Ok(Err(e)) => {
                    state.lock().unwrap().mark_offline(&addr);
                    ctx.emit(SessionEvent::PeerOffline { uid: ctx.uid().to_string(), peer: addr, reason: Some(format!("握手失败: {e}")) });
                    return;
                }
                Err(_) => {
                    state.lock().unwrap().mark_offline(&addr);
                    ctx.emit(SessionEvent::PeerOffline { uid: ctx.uid().to_string(), peer: addr, reason: Some("握手超时".into()) });
                    return;
                }
            },
            _ = ctx.cancel.cancelled() => {
                state.lock().unwrap().mark_offline(&addr);
                ctx.emit(SessionEvent::PeerOffline { uid: ctx.uid().to_string(), peer: addr, reason: Some("已关闭监听".into()) });
                return;
            }
            _ = cancel.cancelled() => {
                state.lock().unwrap().mark_offline(&addr);
                ctx.emit(SessionEvent::PeerOffline { uid: ctx.uid().to_string(), peer: addr, reason: Some("被服务端踢出".into()) });
                return;
            }
        };
        let path = path_seen.lock().unwrap().clone();
        if !expected_path.is_empty() && expected_path != "/" && path != expected_path {
            ctx.info(format!("{addr} 请求路径 {path} 与配置 {expected_path} 不一致，仍已接受"));
        }
        let (mut sink, mut source) = ws.split();
        let peer = Some(addr.clone());
        let reason = loop {
            tokio::select! {
                _ = ctx.cancel.cancelled() => break None,
                _ = cancel.cancelled() => break Some("被服务端踢出".into()),
                msg = source.next() => match msg {
                    Some(Ok(Message::Text(t))) => ctx.inbound(peer.clone(), t.as_bytes().to_vec(), tx.clone()),
                    Some(Ok(Message::Binary(b))) => ctx.inbound(peer.clone(), b.to_vec(), tx.clone()),
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
                            ctx.message(peer.clone(), Direction::Out, &o.bytes);
                        }
                        if o.disconnect { let _ = sink.send(Message::Close(None)).await; break Some("按规则主动断开".into()); }
                    }
                    None => break None,
                },
            }
        };
        let _ = sink.close().await;
        state.lock().unwrap().mark_offline(&addr);
        if !ctx.cancel.is_cancelled() {
            ctx.emit(SessionEvent::PeerOffline { uid: ctx.uid().to_string(), peer: addr, reason });
        }
    }
}

#[async_trait]
impl Session for WsServer {
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
            for p in s.peers.values_mut() { p.online = false; }
            w
        };
        if was_online { self.ctx.offline(Some("已关闭监听".into())); }
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
