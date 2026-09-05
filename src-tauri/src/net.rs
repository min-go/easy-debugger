//! Local network diagnostics: interfaces and port checks.

use serde::Serialize;
use std::time::{Duration, Instant};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Interface {
    pub name: String,
    pub ip: String,
    pub is_ipv6: bool,
    pub is_loopback: bool,
}

pub fn interfaces() -> Vec<Interface> {
    let mut list: Vec<Interface> = if_addrs::get_if_addrs()
        .unwrap_or_default()
        .into_iter()
        .map(|i| Interface { name: i.name.clone(), ip: i.ip().to_string(), is_ipv6: i.ip().is_ipv6(), is_loopback: i.is_loopback() })
        .collect();
    list.sort_by(|a, b| (a.is_loopback, a.is_ipv6, &a.name).cmp(&(b.is_loopback, b.is_ipv6, &b.name)));
    list
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PortCheck {
    pub port: u16,
    pub open: bool,
    pub elapsed_ms: u64,
    pub error: Option<String>,
}

pub async fn check_port(host: &str, port: u16, timeout_ms: u64) -> PortCheck {
    let start = Instant::now();
    let addr = match crate::session::resolve_addr(host, port).await {
        Ok(a) => a,
        Err(e) => return PortCheck { port, open: false, elapsed_ms: start.elapsed().as_millis() as u64, error: Some(e) },
    };
    let r = tokio::time::timeout(Duration::from_millis(timeout_ms.max(100)), tokio::net::TcpStream::connect(addr)).await;
    let elapsed_ms = start.elapsed().as_millis() as u64;
    match r {
        Ok(Ok(_)) => PortCheck { port, open: true, elapsed_ms, error: None },
        Ok(Err(e)) => PortCheck { port, open: false, elapsed_ms, error: Some(e.to_string()) },
        Err(_) => PortCheck { port, open: false, elapsed_ms, error: Some("超时".into()) },
    }
}
