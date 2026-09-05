//! Thin `#[tauri::command]` layer. All logic lives in the other modules.

use crate::codec::{self, Payload, SendRequest};
use crate::config::{AppSettings, Format, SessionConfig, Snippet};
use crate::session::{Outbound, Status};
use crate::{dns, events, net, AppState};
use tauri::{AppHandle, State};

type R<T> = Result<T, String>;

// ---- sessions config ----

#[tauri::command]
pub fn list_sessions(state: State<AppState>) -> Vec<SessionConfig> {
    state.store.sessions()
}

#[tauri::command]
pub fn save_session(state: State<AppState>, config: SessionConfig) -> R<SessionConfig> {
    validate(&config)?;
    state.store.save_session(config)
}

#[tauri::command]
pub async fn delete_sessions(state: State<'_, AppState>, uids: Vec<String>) -> R<()> {
    for uid in &uids {
        state.manager.stop(uid).await;
    }
    state.store.delete_sessions(&uids)
}

#[tauri::command]
pub fn reorder_sessions(state: State<AppState>, uids: Vec<String>) -> R<()> {
    state.store.reorder_sessions(&uids)
}

fn validate(c: &SessionConfig) -> R<()> {
    if c.name.trim().is_empty() {
        return Err("请输入名称".into());
    }
    if !matches!(c.kind, crate::config::Kind::Udp) && c.host.trim().is_empty() {
        return Err("请输入主机地址".into());
    }
    if c.port == 0 && !c.kind.is_server() {
        return Err("端口必须在 1 到 65535 之间".into());
    }
    if c.framing.mode == crate::config::FramingMode::Delimiter && !codec::is_hex(&c.framing.delimiter_hex) {
        return Err("分隔符必须是合法的 16 进制".into());
    }
    for r in &c.auto_reply.rules {
        if r.match_kind == crate::config::MatchKind::Regex {
            regex::Regex::new(&r.pattern).map_err(|e| format!("正则无效: {e}"))?;
        }
        if r.match_kind == crate::config::MatchKind::HexPrefix && !codec::is_hex(&r.pattern) {
            return Err("Hex 前缀规则的内容必须是合法 16 进制".into());
        }
        if r.action == crate::config::ReplyAction::Reply && r.format == Format::Hex && !codec::is_hex(&r.reply) {
            return Err("回复内容必须是合法 16 进制".into());
        }
    }
    if c.timed_send.enabled {
        if c.timed_send.interval_ms == 0 {
            return Err("定时发送间隔必须大于 0".into());
        }
        if c.timed_send.content.trim().is_empty() {
            return Err("请输入定时发送内容".into());
        }
        if c.timed_send.format == Format::Hex && !codec::is_hex(&c.timed_send.content) {
            return Err("定时发送内容必须是合法 16 进制".into());
        }
    }
    Ok(())
}

// ---- runtime ----

#[tauri::command]
pub async fn start_session(app: AppHandle, state: State<'_, AppState>, uid: String) -> R<()> {
    let cfg = state.store.session(&uid).ok_or("配置不存在")?;
    state.manager.start(events::app_emitter(app), cfg).await
}

#[tauri::command]
pub async fn stop_session(state: State<'_, AppState>, uid: String) -> R<()> {
    state.manager.stop(&uid).await;
    Ok(())
}

#[tauri::command]
pub async fn session_status(state: State<'_, AppState>, uid: String) -> R<Status> {
    match state.manager.get(&uid) {
        Some(s) => Ok(s.status().await),
        None => Ok(Status::default()),
    }
}

#[tauri::command]
pub fn running_sessions(state: State<AppState>) -> Vec<String> {
    state.manager.running()
}

#[tauri::command]
pub async fn send_message(state: State<'_, AppState>, uid: String, target: Option<String>, request: SendRequest) -> R<Payload> {
    let session = state.manager.get(&uid).ok_or("会话未启动")?;
    let payload = codec::build_payload(&request)?;
    if payload.bytes.is_empty() {
        return Err("内容为空".into());
    }
    let as_text = !matches!(request.format, Format::Hex | Format::Base64);
    session.send(Outbound { target, bytes: payload.bytes.clone(), as_text, disconnect: false }).await?;
    Ok(payload)
}

#[tauri::command]
pub fn preview_payload(request: SendRequest) -> R<Payload> {
    codec::build_payload(&request)
}

#[tauri::command]
pub async fn kick_peer(state: State<'_, AppState>, uid: String, peer: String) -> R<()> {
    let session = state.manager.get(&uid).ok_or("会话未启动")?;
    session.kick(&peer).await
}

#[tauri::command]
pub fn hexdump(hex: String) -> R<String> {
    Ok(codec::hexdump(&codec::parse_hex(&hex)?))
}

#[tauri::command]
pub fn decode_bytes(hex: String, encoding: String) -> R<String> {
    Ok(codec::decode_text(&codec::parse_hex(&hex)?, &encoding).0)
}

#[tauri::command]
pub fn to_base64(hex: String) -> R<String> {
    Ok(codec::to_base64(&codec::parse_hex(&hex)?))
}

// ---- snippets / settings ----

#[tauri::command]
pub fn list_snippets(state: State<AppState>) -> Vec<Snippet> {
    state.store.snippets()
}

#[tauri::command]
pub fn save_snippet(state: State<AppState>, snippet: Snippet) -> R<Snippet> {
    if snippet.name.trim().is_empty() {
        return Err("请输入片段名称".into());
    }
    state.store.save_snippet(snippet)
}

#[tauri::command]
pub fn delete_snippets(state: State<AppState>, uids: Vec<String>) -> R<()> {
    state.store.delete_snippets(&uids)
}

#[tauri::command]
pub fn get_settings(state: State<AppState>) -> AppSettings {
    state.store.settings()
}

#[tauri::command]
pub fn save_settings(state: State<AppState>, settings: AppSettings) -> R<()> {
    state.store.save_settings(settings)
}

#[tauri::command]
pub fn config_dir(state: State<AppState>) -> String {
    state.store.dir().display().to_string()
}

// ---- dns / net ----

#[tauri::command]
pub async fn dns_query(name: String, record_type: String, server: Option<String>) -> R<dns::DnsResult> {
    dns::query(&name, &record_type, server.as_deref()).await
}

#[tauri::command]
pub async fn resolve_host(host: String) -> R<Vec<dns::Resolved>> {
    dns::resolve_host(&host).await
}

#[tauri::command]
pub fn list_interfaces() -> Vec<net::Interface> {
    net::interfaces()
}

#[tauri::command]
pub async fn check_ports(host: String, ports: Vec<u16>, timeout_ms: Option<u64>) -> Vec<net::PortCheck> {
    let t = timeout_ms.unwrap_or(2000);
    let futs = ports.into_iter().map(|p| net::check_port(&host, p, t));
    futures_util::future::join_all(futs).await
}
