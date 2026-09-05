use super::{resolve_addr, Ctx, Outbound, PeerInfo, Session, Status};
use crate::events::{Direction, SessionEvent};
use async_trait::async_trait;
use std::collections::HashMap;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::sync::{Arc, Mutex};
use tokio::net::UdpSocket;
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

pub struct Udp {
    ctx: Arc<Ctx>,
    tx: mpsc::UnboundedSender<Outbound>,
    rx: Mutex<Option<mpsc::UnboundedReceiver<Outbound>>>,
    state: Arc<Mutex<State>>,
}

#[derive(Default)]
struct State {
    online: bool,
    local: Option<String>,
    peers: HashMap<String, i64>,
    order: Vec<String>,
}

impl Udp {
    pub fn new(ctx: Arc<Ctx>) -> Self {
        let (tx, rx) = mpsc::unbounded_channel();
        Self { ctx, tx, rx: Mutex::new(Some(rx)), state: Arc::default() }
    }
}

#[async_trait]
impl Session for Udp {
    async fn start(&self) -> Result<(), String> {
        let mut rx = self.rx.lock().unwrap().take().ok_or("会话已启动")?;
        let cfg = &self.ctx.cfg;
        let default_target = if cfg.host.trim().is_empty() { None } else { Some(resolve_addr(&cfg.host, cfg.port).await?) };
        let bind_ip: IpAddr = match default_target {
            Some(t) if t.is_ipv6() => IpAddr::V6(std::net::Ipv6Addr::UNSPECIFIED),
            _ => IpAddr::V4(Ipv4Addr::UNSPECIFIED),
        };
        let socket = UdpSocket::bind(SocketAddr::new(bind_ip, cfg.local_port)).await.map_err(|e| format!("绑定端口失败: {e}"))?;
        if cfg.broadcast {
            socket.set_broadcast(true).map_err(|e| format!("开启广播失败: {e}"))?;
        }
        if !cfg.multicast_group.trim().is_empty() {
            match cfg.multicast_group.trim().parse::<IpAddr>() {
                Ok(IpAddr::V4(g)) => {
                    socket.join_multicast_v4(g, Ipv4Addr::UNSPECIFIED).map_err(|e| format!("加入组播组失败: {e}"))?;
                    socket.set_multicast_ttl_v4(cfg.multicast_ttl.max(1)).ok();
                    socket.set_multicast_loop_v4(true).ok();
                }
                Ok(IpAddr::V6(g)) => {
                    socket.join_multicast_v6(&g, 0).map_err(|e| format!("加入组播组失败: {e}"))?;
                }
                Err(_) => return Err("组播地址无效".into()),
            }
        }
        let local = socket.local_addr().ok().map(|a| a.to_string());
        {
            let mut s = self.state.lock().unwrap();
            s.online = true;
            s.local = local.clone();
        }
        self.ctx.online(local);
        let ctx = self.ctx.clone();
        let state = self.state.clone();
        let tx = self.tx.clone();
        let socket = Arc::new(socket);
        let timed_stop = CancellationToken::new();
        ctx.spawn_timed_send(tx.clone(), timed_stop.clone());
        tokio::spawn(async move {
            let mut buf = vec![0u8; 65536];
            loop {
                tokio::select! {
                    _ = ctx.cancel.cancelled() => break,
                    r = socket.recv_from(&mut buf) => match r {
                        Ok((n, from)) => {
                            let addr = from.to_string();
                            let is_new = {
                                let mut s = state.lock().unwrap();
                                let is_new = !s.peers.contains_key(&addr);
                                if is_new { s.order.push(addr.clone()); }
                                s.peers.insert(addr.clone(), crate::events::now_ms());
                                is_new
                            };
                            if is_new {
                                ctx.emit(SessionEvent::PeerOnline { uid: ctx.uid().to_string(), peer: addr.clone() });
                            }
                            ctx.inbound(Some(addr), buf[..n].to_vec(), tx.clone());
                        }
                        Err(e) => { ctx.error(format!("接收失败: {e}")); }
                    },
                    out = rx.recv() => match out {
                        Some(o) => {
                            let target: Option<SocketAddr> = match &o.target {
                                Some(t) => t.parse().ok(),
                                None => default_target,
                            };
                            let Some(target) = target else { ctx.error("没有目标地址，请在配置里填写主机和端口"); continue; };
                            match socket.send_to(&o.bytes, target).await {
                                Ok(_) => ctx.message(Some(target.to_string()), Direction::Out, &o.bytes),
                                Err(e) => ctx.error(format!("发送失败: {e}")),
                            }
                        }
                        None => break,
                    }
                }
            }
            timed_stop.cancel();
        });
        Ok(())
    }

    async fn stop(&self) {
        self.ctx.cancel.cancel();
        let was_online = {
            let mut s = self.state.lock().unwrap();
            let w = s.online;
            s.online = false;
            w
        };
        if was_online {
            self.ctx.offline(Some("已关闭".into()));
        }
    }

    async fn send(&self, out: Outbound) -> Result<(), String> {
        if !self.state.lock().unwrap().online {
            return Err("未启动".into());
        }
        self.tx.send(out).map_err(|_| "会话已关闭".to_string())
    }

    async fn status(&self) -> Status {
        let s = self.state.lock().unwrap();
        Status {
            online: s.online,
            local: s.local.clone(),
            peers: s.order.iter().map(|a| PeerInfo { addr: a.clone(), online: true, since: *s.peers.get(a).unwrap_or(&0) }).collect(),
        }
    }
}
