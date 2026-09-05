//! Session configuration model and JSON persistence.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use std::sync::Mutex;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Kind {
    TcpClient,
    TcpServer,
    Udp,
    WsClient,
    WsServer,
}

impl Kind {
    pub fn is_server(self) -> bool {
        matches!(self, Kind::TcpServer | Kind::WsServer)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum Format {
    #[default]
    Text,
    Hex,
    Base64,
    Json,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum LineEnding {
    #[default]
    None,
    Lf,
    CrLf,
    Cr,
    Nul,
    Custom,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum Checksum {
    #[default]
    None,
    Crc16Modbus,
    Crc16Ccitt,
    Crc32,
    Xor,
    Sum8,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum FramingMode {
    #[default]
    None,
    Delimiter,
    LengthPrefix,
    Fixed,
    Timeout,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Framing {
    pub mode: FramingMode,
    pub delimiter_hex: String,
    pub len_offset: u32,
    pub len_size: u32,
    pub big_endian: bool,
    pub len_includes_header: bool,
    pub fixed_len: u32,
    pub timeout_ms: u32,
}

impl Default for Framing {
    fn default() -> Self {
        Self {
            mode: FramingMode::None,
            delimiter_hex: "0A".into(),
            len_offset: 0,
            len_size: 2,
            big_endian: true,
            len_includes_header: false,
            fixed_len: 8,
            timeout_ms: 50,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum MatchKind {
    #[default]
    Exact,
    Prefix,
    Contains,
    Regex,
    HexPrefix,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum ReplyAction {
    #[default]
    Reply,
    Echo,
    Disconnect,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct ReplyRule {
    pub enabled: bool,
    pub match_kind: MatchKind,
    pub pattern: String,
    pub action: ReplyAction,
    pub format: Format,
    pub reply: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct AutoReply {
    pub enabled: bool,
    pub delay_ms: u32,
    pub rules: Vec<ReplyRule>,
    pub default_enabled: bool,
    pub default_format: Format,
    pub default_reply: String,
}

impl Default for AutoReply {
    fn default() -> Self {
        Self {
            enabled: false,
            delay_ms: 0,
            rules: vec![],
            default_enabled: false,
            default_format: Format::Text,
            default_reply: String::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct TimedSend {
    pub enabled: bool,
    pub interval_ms: u32,
    pub count: u32,
    pub format: Format,
    pub content: String,
}

impl Default for TimedSend {
    fn default() -> Self {
        Self {
            enabled: false,
            interval_ms: 1000,
            count: 0,
            format: Format::Text,
            content: String::new(),
        }
    }
}

/// Everything the user can configure for one session.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct SessionConfig {
    pub uid: String,
    pub name: String,
    pub kind: Kind,
    pub host: String,
    pub port: u16,
    pub group: String,

    // TCP client
    pub connect_timeout_ms: u32,
    pub auto_reconnect: bool,
    pub reconnect_interval_ms: u32,
    pub reconnect_max: u32,
    pub local_bind: String,
    pub nodelay: bool,
    pub keepalive: bool,

    // server
    pub max_connections: u32,

    // UDP
    pub local_port: u16,
    pub broadcast: bool,
    pub multicast_group: String,
    pub multicast_ttl: u32,

    // WebSocket
    pub ws_path: String,
    pub ws_headers: Vec<(String, String)>,

    // encoding & framing
    pub send_encoding: String,
    pub recv_encoding: String,
    pub framing: Framing,

    pub auto_reply: AutoReply,
    pub timed_send: TimedSend,
}

impl Default for SessionConfig {
    fn default() -> Self {
        Self {
            uid: String::new(),
            name: String::new(),
            kind: Kind::TcpClient,
            host: "127.0.0.1".into(),
            port: 8080,
            group: String::new(),
            connect_timeout_ms: 5000,
            auto_reconnect: false,
            reconnect_interval_ms: 3000,
            reconnect_max: 0,
            local_bind: String::new(),
            nodelay: true,
            keepalive: true,
            max_connections: 0,
            local_port: 0,
            broadcast: false,
            multicast_group: String::new(),
            multicast_ttl: 1,
            ws_path: "/".into(),
            ws_headers: vec![],
            send_encoding: "utf-8".into(),
            recv_encoding: "utf-8".into(),
            framing: Framing::default(),
            auto_reply: AutoReply::default(),
            timed_send: TimedSend::default(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct Snippet {
    pub uid: String,
    pub name: String,
    pub group: String,
    pub format: Format,
    pub content: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct AppSettings {
    pub theme: String,
    pub language: String,
    pub max_messages: u32,
    pub font_size: u32,
    pub restore_sessions: bool,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            theme: "system".into(),
            language: "system".into(),
            max_messages: 5000,
            font_size: 13,
            restore_sessions: false,
        }
    }
}

/// On-disk store. One JSON file per collection, rewritten whole on every change.
pub struct Store {
    dir: PathBuf,
    sessions: Mutex<Vec<SessionConfig>>,
    snippets: Mutex<Vec<Snippet>>,
    settings: Mutex<AppSettings>,
}

fn load_json<T: Default + for<'de> Deserialize<'de>>(path: &PathBuf) -> T {
    match fs::read(path) {
        Ok(bytes) => serde_json::from_slice(&bytes).unwrap_or_else(|e| {
            log::warn!("failed to parse {}: {e}", path.display());
            T::default()
        }),
        Err(_) => T::default(),
    }
}

fn save_json<T: Serialize>(path: &PathBuf, value: &T) -> Result<(), String> {
    let json = serde_json::to_vec_pretty(value).map_err(|e| e.to_string())?;
    let tmp = path.with_extension("json.tmp");
    fs::write(&tmp, json).map_err(|e| e.to_string())?;
    fs::rename(&tmp, path).map_err(|e| e.to_string())
}

pub fn new_uid() -> String {
    uuid::Uuid::new_v4().simple().to_string()
}

impl Store {
    pub fn open(dir: PathBuf) -> Self {
        if let Err(e) = fs::create_dir_all(&dir) {
            log::error!("cannot create config dir {}: {e}", dir.display());
        }
        let sessions = load_json(&dir.join("sessions.json"));
        let snippets = load_json(&dir.join("snippets.json"));
        let settings = load_json(&dir.join("settings.json"));
        Self {
            dir,
            sessions: Mutex::new(sessions),
            snippets: Mutex::new(snippets),
            settings: Mutex::new(settings),
        }
    }

    pub fn dir(&self) -> &PathBuf {
        &self.dir
    }

    // ---- sessions ----
    pub fn sessions(&self) -> Vec<SessionConfig> {
        self.sessions.lock().unwrap().clone()
    }

    pub fn session(&self, uid: &str) -> Option<SessionConfig> {
        self.sessions.lock().unwrap().iter().find(|s| s.uid == uid).cloned()
    }

    pub fn save_session(&self, mut cfg: SessionConfig) -> Result<SessionConfig, String> {
        let mut list = self.sessions.lock().unwrap();
        if cfg.uid.is_empty() {
            cfg.uid = new_uid();
            list.push(cfg.clone());
        } else if let Some(slot) = list.iter_mut().find(|s| s.uid == cfg.uid) {
            *slot = cfg.clone();
        } else {
            list.push(cfg.clone());
        }
        save_json(&self.dir.join("sessions.json"), &*list)?;
        Ok(cfg)
    }

    pub fn delete_sessions(&self, uids: &[String]) -> Result<(), String> {
        let mut list = self.sessions.lock().unwrap();
        list.retain(|s| !uids.contains(&s.uid));
        save_json(&self.dir.join("sessions.json"), &*list)
    }

    pub fn reorder_sessions(&self, uids: &[String]) -> Result<(), String> {
        let mut list = self.sessions.lock().unwrap();
        let mut ordered: Vec<SessionConfig> = uids
            .iter()
            .filter_map(|u| list.iter().find(|s| &s.uid == u).cloned())
            .collect();
        for s in list.iter() {
            if !uids.contains(&s.uid) {
                ordered.push(s.clone());
            }
        }
        *list = ordered;
        save_json(&self.dir.join("sessions.json"), &*list)
    }

    // ---- snippets ----
    pub fn snippets(&self) -> Vec<Snippet> {
        self.snippets.lock().unwrap().clone()
    }

    pub fn save_snippet(&self, mut s: Snippet) -> Result<Snippet, String> {
        let mut list = self.snippets.lock().unwrap();
        if s.uid.is_empty() {
            s.uid = new_uid();
            list.push(s.clone());
        } else if let Some(slot) = list.iter_mut().find(|x| x.uid == s.uid) {
            *slot = s.clone();
        } else {
            list.push(s.clone());
        }
        save_json(&self.dir.join("snippets.json"), &*list)?;
        Ok(s)
    }

    pub fn delete_snippets(&self, uids: &[String]) -> Result<(), String> {
        let mut list = self.snippets.lock().unwrap();
        list.retain(|s| !uids.contains(&s.uid));
        save_json(&self.dir.join("snippets.json"), &*list)
    }

    // ---- settings ----
    pub fn settings(&self) -> AppSettings {
        self.settings.lock().unwrap().clone()
    }

    pub fn save_settings(&self, s: AppSettings) -> Result<(), String> {
        let mut cur = self.settings.lock().unwrap();
        *cur = s;
        save_json(&self.dir.join("settings.json"), &*cur)
    }
}
