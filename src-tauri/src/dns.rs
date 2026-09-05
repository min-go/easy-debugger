//! DNS lookups via hickory-resolver, with optional custom nameserver.

use hickory_resolver::config::{NameServerConfigGroup, ResolverConfig, ResolverOpts};
use hickory_resolver::name_server::TokioConnectionProvider;
use hickory_resolver::proto::rr::RecordType;
use hickory_resolver::TokioResolver;
use serde::Serialize;
use std::net::IpAddr;
use std::time::{Duration, Instant};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DnsRecord {
    pub name: String,
    pub record_type: String,
    pub value: String,
    pub ttl: u32,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DnsResult {
    pub query: String,
    pub record_type: String,
    pub server: String,
    pub elapsed_ms: u64,
    pub records: Vec<DnsRecord>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Resolved {
    pub ip: String,
    pub record_type: String,
    pub elapsed_ms: u64,
}

fn build_resolver(server: Option<&str>) -> Result<(TokioResolver, String), String> {
    let mut opts = ResolverOpts::default();
    opts.timeout = Duration::from_secs(4);
    opts.attempts = 1;
    opts.cache_size = 0;
    match server.map(str::trim).filter(|s| !s.is_empty()) {
        Some(s) => {
            let (ip, port) = match s.rsplit_once(':') {
                Some((h, p)) if !h.contains(':') || h.starts_with('[') => (h.trim_matches(['[', ']']), p.parse::<u16>().map_err(|_| "DNS 服务器端口无效")?),
                _ => (s, 53),
            };
            let ip: IpAddr = ip.parse().map_err(|_| format!("DNS 服务器地址无效: {s}"))?;
            let group = NameServerConfigGroup::from_ips_clear(&[ip], port, true);
            let config = ResolverConfig::from_parts(None, vec![], group);
            let mut builder = TokioResolver::builder_with_config(config, TokioConnectionProvider::default());
            *builder.options_mut() = opts;
            Ok((builder.build(), format!("{ip}:{port}")))
        }
        None => {
            let mut builder = TokioResolver::builder_tokio().map_err(|e| format!("读取系统 DNS 配置失败: {e}"))?;
            let sys_opts = builder.options_mut();
            sys_opts.timeout = opts.timeout;
            sys_opts.attempts = opts.attempts;
            sys_opts.cache_size = 0;
            Ok((builder.build(), "系统默认".to_string()))
        }
    }
}

fn parse_type(t: &str) -> Result<RecordType, String> {
    t.to_ascii_uppercase().parse::<RecordType>().map_err(|_| format!("不支持的记录类型: {t}"))
}

pub async fn query(name: &str, record_type: &str, server: Option<&str>) -> Result<DnsResult, String> {
    let name = name.trim();
    if name.is_empty() {
        return Err("请输入域名".into());
    }
    let (resolver, server_label) = build_resolver(server)?;
    let rt = parse_type(record_type)?;
    let start = Instant::now();
    let records = if rt == RecordType::PTR {
        let ip: IpAddr = name.parse().map_err(|_| "PTR 查询请输入 IP 地址")?;
        let lookup = resolver.reverse_lookup(ip).await.map_err(|e| friendly(e.to_string()))?;
        lookup
            .as_lookup()
            .record_iter()
            .map(|r| DnsRecord { name: r.name().to_string(), record_type: r.record_type().to_string(), value: r.data().to_string(), ttl: r.ttl() })
            .collect()
    } else {
        let lookup = resolver.lookup(name, rt).await.map_err(|e| friendly(e.to_string()))?;
        lookup
            .record_iter()
            .map(|r| DnsRecord { name: r.name().to_string(), record_type: r.record_type().to_string(), value: r.data().to_string(), ttl: r.ttl() })
            .collect()
    };
    Ok(DnsResult { query: name.to_string(), record_type: rt.to_string(), server: server_label, elapsed_ms: start.elapsed().as_millis() as u64, records })
}

/// A + AAAA for the connect dialog.
pub async fn resolve_host(host: &str) -> Result<Vec<Resolved>, String> {
    let host = host.trim();
    if let Ok(ip) = host.parse::<IpAddr>() {
        return Ok(vec![Resolved { ip: ip.to_string(), record_type: if ip.is_ipv4() { "A" } else { "AAAA" }.into(), elapsed_ms: 0 }]);
    }
    let (resolver, _) = build_resolver(None)?;
    let start = Instant::now();
    let mut out = vec![];
    let (a, aaaa) = tokio::join!(resolver.ipv4_lookup(host), resolver.ipv6_lookup(host));
    let elapsed_ms = start.elapsed().as_millis() as u64;
    if let Ok(l) = &a {
        for ip in l.iter() {
            out.push(Resolved { ip: ip.to_string(), record_type: "A".into(), elapsed_ms });
        }
    }
    if let Ok(l) = &aaaa {
        for ip in l.iter() {
            out.push(Resolved { ip: ip.to_string(), record_type: "AAAA".into(), elapsed_ms });
        }
    }
    if out.is_empty() {
        let msg = match a { Err(e) => friendly(e.to_string()), Ok(_) => "没有解析到地址".into() };
        return Err(msg);
    }
    Ok(out)
}

fn friendly(e: String) -> String {
    if e.contains("no record found") || e.contains("NXDomain") {
        "没有找到记录".into()
    } else if e.contains("timed out") || e.contains("timeout") {
        "查询超时".into()
    } else {
        e
    }
}
