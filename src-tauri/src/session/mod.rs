//! Session abstraction shared by every transport, plus auto-reply and timed-send helpers.

pub mod tcp_client;
pub mod tcp_server;
pub mod udp;
pub mod ws_client;
pub mod ws_server;

#[cfg(test)]
mod tests;

use crate::codec::{self, SendRequest};
use crate::config::{AutoReply, Format, Kind, MatchKind, ReplyAction, SessionConfig};
use crate::events::{self, Direction, Emitter, SessionEvent};
use async_trait::async_trait;
use serde::Serialize;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

/// Something to write to the wire.
#[derive(Debug, Clone)]
pub struct Outbound {
    /// Server side: which peer. `None` = all online peers (server) or the only peer (client).
    pub target: Option<String>,
    pub bytes: Vec<u8>,
    /// WebSocket: send as a Text frame instead of Binary.
    pub as_text: bool,
    /// Close the connection after writing (auto-reply "disconnect" action).
    pub disconnect: bool,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct PeerInfo {
    pub addr: String,
    pub online: bool,
    pub since: i64,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub online: bool,
    pub local: Option<String>,
    pub peers: Vec<PeerInfo>,
}

#[async_trait]
pub trait Session: Send + Sync {
    /// Start connecting / listening. Errors that happen immediately (bad address, bind failure)
    /// come back here; later failures arrive as events.
    async fn start(&self) -> Result<(), String>;
    async fn stop(&self);
    async fn send(&self, out: Outbound) -> Result<(), String>;
    async fn status(&self) -> Status;
    async fn kick(&self, _peer: &str) -> Result<(), String> {
        Err("该会话类型不支持踢出对端".into())
    }
}

/// Shared context handed to every transport.
pub struct Ctx {
    pub emitter: Emitter,
    pub cfg: SessionConfig,
    pub cancel: CancellationToken,
}

impl Ctx {
    pub fn new(emitter: Emitter, cfg: SessionConfig) -> Arc<Self> {
        Arc::new(Self { emitter, cfg, cancel: CancellationToken::new() })
    }

    pub fn uid(&self) -> &str {
        &self.cfg.uid
    }

    pub fn emit(&self, ev: SessionEvent) {
        (self.emitter)(ev);
    }

    pub fn online(&self, local: Option<String>) {
        self.emit(SessionEvent::Online { uid: self.cfg.uid.clone(), local });
    }

    pub fn offline(&self, reason: Option<String>) {
        self.emit(SessionEvent::Offline { uid: self.cfg.uid.clone(), reason });
    }

    pub fn error(&self, message: impl Into<String>) {
        self.emit(SessionEvent::Error { uid: self.cfg.uid.clone(), message: message.into() });
    }

    pub fn info(&self, message: impl Into<String>) {
        self.emit(SessionEvent::Info { uid: self.cfg.uid.clone(), message: message.into() });
    }

    pub fn message(&self, peer: Option<String>, direction: Direction, bytes: &[u8]) {
        let (is_text, text) = codec::classify(bytes, &self.cfg.recv_encoding);
        self.emit(SessionEvent::Message {
            uid: self.cfg.uid.clone(),
            peer,
            direction,
            hex: codec::to_hex(bytes),
            text: if is_text { text } else { String::new() },
            is_text,
            len: bytes.len(),
            ts: events::now_ms(),
        });
    }

    /// Record an inbound frame and schedule any auto-reply. `reply` delivers the outbound.
    pub fn inbound(self: &Arc<Self>, peer: Option<String>, bytes: Vec<u8>, reply: mpsc::UnboundedSender<Outbound>) {
        self.message(peer.clone(), Direction::In, &bytes);
        let ar = &self.cfg.auto_reply;
        if !ar.enabled {
            return;
        }
        if let Some(plan) = evaluate_auto_reply(ar, &bytes, &self.cfg.recv_encoding, &self.cfg.send_encoding) {
            let delay = ar.delay_ms as u64;
            let out = Outbound { target: peer, bytes: plan.bytes, as_text: plan.as_text, disconnect: plan.disconnect };
            let cancel = self.cancel.clone();
            tokio::spawn(async move {
                if delay > 0 {
                    tokio::select! {
                        _ = tokio::time::sleep(std::time::Duration::from_millis(delay)) => {}
                        _ = cancel.cancelled() => return,
                    }
                }
                let _ = reply.send(out);
            });
        }
    }

    /// Spawn the timed-send loop if configured. Stops when `stop` or the session is cancelled.
    pub fn spawn_timed_send(self: &Arc<Self>, tx: mpsc::UnboundedSender<Outbound>, stop: CancellationToken) {
        let ts = &self.cfg.timed_send;
        if !ts.enabled || ts.interval_ms == 0 || ts.content.is_empty() {
            return;
        }
        let ctx = self.clone();
        let interval = std::time::Duration::from_millis(ts.interval_ms as u64);
        let count = ts.count;
        let req = SendRequest { format: ts.format, content: ts.content.clone(), encoding: self.cfg.send_encoding.clone(), template: true, ..Default::default() };
        tokio::spawn(async move {
            let mut sent = 0u32;
            loop {
                tokio::select! {
                    _ = tokio::time::sleep(interval) => {}
                    _ = stop.cancelled() => return,
                    _ = ctx.cancel.cancelled() => return,
                }
                match codec::build_payload(&req) {
                    Ok(p) => {
                        let _ = tx.send(Outbound { target: None, bytes: p.bytes, as_text: !matches!(req.format, Format::Hex | Format::Base64), disconnect: false });
                    }
                    Err(e) => {
                        ctx.error(format!("定时发送内容无效: {e}"));
                        return;
                    }
                }
                sent += 1;
                if count > 0 && sent >= count {
                    ctx.info(format!("定时发送完成，共 {sent} 次"));
                    return;
                }
            }
        });
    }
}

pub struct ReplyPlan {
    pub bytes: Vec<u8>,
    pub as_text: bool,
    pub disconnect: bool,
}

pub fn evaluate_auto_reply(ar: &AutoReply, inbound: &[u8], recv_enc: &str, send_enc: &str) -> Option<ReplyPlan> {
    let (text, _) = codec::decode_text(inbound, recv_enc);
    let render = |format: Format, content: &str| -> Option<(Vec<u8>, bool)> {
        let req = SendRequest { format, content: content.to_string(), encoding: send_enc.to_string(), template: true, ..Default::default() };
        codec::build_payload(&req).ok().map(|p| (p.bytes, !matches!(format, Format::Hex | Format::Base64)))
    };
    for rule in ar.rules.iter().filter(|r| r.enabled) {
        let hit = match rule.match_kind {
            MatchKind::Exact => text.trim_end_matches(['\r', '\n']) == rule.pattern,
            MatchKind::Prefix => text.starts_with(&rule.pattern),
            MatchKind::Contains => text.contains(&rule.pattern),
            MatchKind::Regex => regex::Regex::new(&rule.pattern).map(|re| re.is_match(&text)).unwrap_or(false),
            MatchKind::HexPrefix => codec::parse_hex(&rule.pattern).map(|p| inbound.starts_with(&p)).unwrap_or(false),
        };
        if !hit {
            continue;
        }
        return match rule.action {
            ReplyAction::Echo => Some(ReplyPlan { bytes: inbound.to_vec(), as_text: true, disconnect: false }),
            ReplyAction::Disconnect => Some(ReplyPlan { bytes: vec![], as_text: true, disconnect: true }),
            ReplyAction::Reply => render(rule.format, &rule.reply).map(|(b, t)| ReplyPlan { bytes: b, as_text: t, disconnect: false }),
        };
    }
    if ar.default_enabled && !ar.default_reply.is_empty() {
        return render(ar.default_format, &ar.default_reply).map(|(b, t)| ReplyPlan { bytes: b, as_text: t, disconnect: false });
    }
    None
}

/// Holds every running session, keyed by config uid.
#[derive(Default)]
pub struct Manager {
    sessions: Mutex<HashMap<String, Arc<dyn Session>>>,
}

impl Manager {
    pub fn get(&self, uid: &str) -> Option<Arc<dyn Session>> {
        self.sessions.lock().unwrap().get(uid).cloned()
    }

    pub fn running(&self) -> Vec<String> {
        self.sessions.lock().unwrap().keys().cloned().collect()
    }

    pub async fn start(&self, emitter: Emitter, cfg: SessionConfig) -> Result<(), String> {
        if let Some(existing) = self.get(&cfg.uid) {
            existing.stop().await;
            self.sessions.lock().unwrap().remove(&cfg.uid);
        }
        let ctx = Ctx::new(emitter, cfg.clone());
        let session: Arc<dyn Session> = match cfg.kind {
            Kind::TcpClient => Arc::new(tcp_client::TcpClient::new(ctx)),
            Kind::TcpServer => Arc::new(tcp_server::TcpServer::new(ctx)),
            Kind::Udp => Arc::new(udp::Udp::new(ctx)),
            Kind::WsClient => Arc::new(ws_client::WsClient::new(ctx)),
            Kind::WsServer => Arc::new(ws_server::WsServer::new(ctx)),
        };
        session.start().await?;
        self.sessions.lock().unwrap().insert(cfg.uid.clone(), session);
        Ok(())
    }

    pub async fn stop(&self, uid: &str) {
        let s = self.sessions.lock().unwrap().remove(uid);
        if let Some(s) = s {
            s.stop().await;
        }
    }

    pub async fn stop_all(&self) {
        let all: Vec<Arc<dyn Session>> = self.sessions.lock().unwrap().drain().map(|(_, s)| s).collect();
        for s in all {
            s.stop().await;
        }
    }
}

/// Apply a TCP stream's socket options from config.
pub fn apply_tcp_opts(stream: &tokio::net::TcpStream, cfg: &SessionConfig) {
    let _ = stream.set_nodelay(cfg.nodelay);
    // SO_KEEPALIVE via socket2 would need another crate; tokio exposes nodelay only.
    // Keepalive is left to the OS default; the flag is kept for future use.
}

/// Resolve `host:port` to the first socket address, honoring IPv6 literals.
pub async fn resolve_addr(host: &str, port: u16) -> Result<std::net::SocketAddr, String> {
    let host = host.trim().trim_start_matches('[').trim_end_matches(']');
    if let Ok(ip) = host.parse::<std::net::IpAddr>() {
        return Ok(std::net::SocketAddr::new(ip, port));
    }
    let mut addrs = tokio::net::lookup_host((host, port)).await.map_err(|e| format!("域名解析失败: {e}"))?;
    addrs.next().ok_or_else(|| "域名没有解析到任何地址".to_string())
}
