//! Events pushed from Rust to the webview. One event name, tagged payload.

use serde::Serialize;
use tauri::{AppHandle, Emitter as _};

pub const EVENT: &str = "session-event";

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Direction {
    In,
    Out,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
pub enum SessionEvent {
    Online { uid: String, local: Option<String> },
    Offline { uid: String, reason: Option<String> },
    Reconnecting { uid: String, attempt: u32 },
    PeerOnline { uid: String, peer: String },
    PeerOffline { uid: String, peer: String, reason: Option<String> },
    Message {
        uid: String,
        peer: Option<String>,
        direction: Direction,
        hex: String,
        text: String,
        is_text: bool,
        len: usize,
        ts: i64,
    },
    Error { uid: String, message: String },
    Info { uid: String, message: String },
}

/// Anything that can deliver events to the UI. The app wraps `AppHandle::emit`; tests use a channel.
pub type Emitter = std::sync::Arc<dyn Fn(SessionEvent) + Send + Sync>;

pub fn app_emitter(app: AppHandle) -> Emitter {
    std::sync::Arc::new(move |ev| emit(&app, ev))
}

pub fn emit(app: &AppHandle, ev: SessionEvent) {
    if let Err(e) = app.emit(EVENT, &ev) {
        log::warn!("emit failed: {e}");
    }
}

pub fn now_ms() -> i64 {
    chrono::Local::now().timestamp_millis()
}
