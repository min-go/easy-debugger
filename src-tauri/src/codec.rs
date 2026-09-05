//! Byte-level helpers: hex parsing, encodings, escapes, checksums, templates, display.

use crate::config::{Checksum, Format, LineEnding};
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicU64, Ordering};

static SEQ: AtomicU64 = AtomicU64::new(1);

// ---------- hex ----------

/// Lenient hex parser: accepts spaces, commas, newlines, `0x` prefixes, any case.
pub fn parse_hex(input: &str) -> Result<Vec<u8>, String> {
    let mut digits = String::with_capacity(input.len());
    for token in input.split(|c: char| c.is_whitespace() || c == ',') {
        let t = token.trim_start_matches("0x").trim_start_matches("0X");
        digits.push_str(t);
    }
    if digits.is_empty() {
        return Ok(vec![]);
    }
    if digits.len() % 2 != 0 {
        return Err("16 进制长度必须为偶数".into());
    }
    let mut out = Vec::with_capacity(digits.len() / 2);
    let bytes = digits.as_bytes();
    for i in (0..bytes.len()).step_by(2) {
        let hi = hex_val(bytes[i]).ok_or_else(|| format!("非法 16 进制字符 '{}'", bytes[i] as char))?;
        let lo = hex_val(bytes[i + 1]).ok_or_else(|| format!("非法 16 进制字符 '{}'", bytes[i + 1] as char))?;
        out.push(hi << 4 | lo);
    }
    Ok(out)
}

fn hex_val(c: u8) -> Option<u8> {
    match c {
        b'0'..=b'9' => Some(c - b'0'),
        b'a'..=b'f' => Some(c - b'a' + 10),
        b'A'..=b'F' => Some(c - b'A' + 10),
        _ => None,
    }
}

pub fn is_hex(input: &str) -> bool {
    parse_hex(input).is_ok()
}

/// Uppercase, space separated: `48 65 6C`.
pub fn to_hex(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 3);
    for (i, b) in bytes.iter().enumerate() {
        if i > 0 {
            s.push(' ');
        }
        s.push_str(&format!("{b:02X}"));
    }
    s
}

/// xxd-style dump: offset, 16 bytes, ascii column.
pub fn hexdump(bytes: &[u8]) -> String {
    let mut out = String::new();
    for (row, chunk) in bytes.chunks(16).enumerate() {
        out.push_str(&format!("{:08X}  ", row * 16));
        for i in 0..16 {
            if i == 8 {
                out.push(' ');
            }
            match chunk.get(i) {
                Some(b) => out.push_str(&format!("{b:02X} ")),
                None => out.push_str("   "),
            }
        }
        out.push(' ');
        for b in chunk {
            out.push(if (0x20..0x7f).contains(b) { *b as char } else { '.' });
        }
        out.push('\n');
    }
    out.trim_end().to_string()
}

// ---------- encodings ----------

pub fn encoding_for(label: &str) -> &'static encoding_rs::Encoding {
    encoding_rs::Encoding::for_label(label.as_bytes()).unwrap_or(encoding_rs::UTF_8)
}

pub fn encode_text(text: &str, encoding: &str) -> Vec<u8> {
    match encoding.to_ascii_lowercase().as_str() {
        "utf-16le" => text.encode_utf16().flat_map(|u| u.to_le_bytes()).collect(),
        "utf-16be" => text.encode_utf16().flat_map(|u| u.to_be_bytes()).collect(),
        "ascii" | "latin-1" | "latin1" | "iso-8859-1" => text.chars().map(|c| if (c as u32) < 256 { c as u8 } else { b'?' }).collect(),
        other => encoding_for(other).encode(text).0.into_owned(),
    }
}

/// Decode bytes with the given encoding. Returns (text, had_errors).
pub fn decode_text(bytes: &[u8], encoding: &str) -> (String, bool) {
    match encoding.to_ascii_lowercase().as_str() {
        "utf-16le" => {
            let units: Vec<u16> = bytes.chunks(2).map(|c| u16::from_le_bytes([c[0], *c.get(1).unwrap_or(&0)])).collect();
            let s = String::from_utf16_lossy(&units);
            (s, bytes.len() % 2 != 0)
        }
        "utf-16be" => {
            let units: Vec<u16> = bytes.chunks(2).map(|c| u16::from_be_bytes([c[0], *c.get(1).unwrap_or(&0)])).collect();
            let s = String::from_utf16_lossy(&units);
            (s, bytes.len() % 2 != 0)
        }
        "ascii" => {
            let bad = bytes.iter().any(|b| *b > 0x7f);
            (bytes.iter().map(|b| if *b > 0x7f { '?' } else { *b as char }).collect(), bad)
        }
        "latin-1" | "latin1" | "iso-8859-1" => (bytes.iter().map(|b| *b as char).collect(), false),
        other => {
            let (cow, _, had_errors) = encoding_for(other).decode(bytes);
            (cow.into_owned(), had_errors)
        }
    }
}

// ---------- escapes ----------

/// Expand `\r \n \t \0 \\ \xHH \uHHHH` in text.
pub fn unescape(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    let chars: Vec<char> = text.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if c != '\\' || i + 1 >= chars.len() {
            out.push(c);
            i += 1;
            continue;
        }
        let n = chars[i + 1];
        match n {
            'r' => { out.push('\r'); i += 2; }
            'n' => { out.push('\n'); i += 2; }
            't' => { out.push('\t'); i += 2; }
            '0' => { out.push('\0'); i += 2; }
            '\\' => { out.push('\\'); i += 2; }
            'x' if i + 3 < chars.len() + 0 && i + 3 <= chars.len() - 0 => {
                let hex: String = chars[i + 2..(i + 4).min(chars.len())].iter().collect();
                if hex.len() == 2 {
                    if let Ok(v) = u8::from_str_radix(&hex, 16) {
                        out.push(v as char);
                        i += 4;
                        continue;
                    }
                }
                out.push(c);
                i += 1;
            }
            'u' if i + 5 < chars.len() + 0 && i + 6 <= chars.len() => {
                let hex: String = chars[i + 2..i + 6].iter().collect();
                if let Ok(v) = u32::from_str_radix(&hex, 16) {
                    if let Some(ch) = char::from_u32(v) {
                        out.push(ch);
                        i += 6;
                        continue;
                    }
                }
                out.push(c);
                i += 1;
            }
            _ => { out.push(c); i += 1; }
        }
    }
    out
}

// ---------- checksums ----------

pub fn checksum(bytes: &[u8], kind: Checksum, big_endian: bool) -> Vec<u8> {
    match kind {
        Checksum::None => vec![],
        Checksum::Crc16Modbus => {
            let v = crc::Crc::<u16>::new(&crc::CRC_16_MODBUS).checksum(bytes);
            if big_endian { v.to_be_bytes().to_vec() } else { v.to_le_bytes().to_vec() }
        }
        Checksum::Crc16Ccitt => {
            let v = crc::Crc::<u16>::new(&crc::CRC_16_IBM_SDLC).checksum(bytes);
            if big_endian { v.to_be_bytes().to_vec() } else { v.to_le_bytes().to_vec() }
        }
        Checksum::Crc32 => {
            let v = crc::Crc::<u32>::new(&crc::CRC_32_ISO_HDLC).checksum(bytes);
            if big_endian { v.to_be_bytes().to_vec() } else { v.to_le_bytes().to_vec() }
        }
        Checksum::Xor => vec![bytes.iter().fold(0u8, |a, b| a ^ b)],
        Checksum::Sum8 => vec![bytes.iter().fold(0u8, |a, b| a.wrapping_add(*b))],
    }
}

// ---------- templates ----------

/// Expand `{{ts}} {{ts_ms}} {{datetime}} {{seq}} {{rand:N}} {{rand_hex:N}} {{uuid}}`.
pub fn expand_template(text: &str) -> String {
    if !text.contains("{{") {
        return text.to_string();
    }
    let re = regex::Regex::new(r"\{\{\s*([a-z_]+)(?::(\d+))?\s*\}\}").unwrap();
    let now = chrono::Local::now();
    re.replace_all(text, |caps: &regex::Captures| {
        let n: usize = caps.get(2).and_then(|m| m.as_str().parse().ok()).unwrap_or(4);
        match &caps[1] {
            "ts" => now.timestamp().to_string(),
            "ts_ms" => now.timestamp_millis().to_string(),
            "datetime" => now.format("%Y-%m-%d %H:%M:%S").to_string(),
            "seq" => SEQ.fetch_add(1, Ordering::Relaxed).to_string(),
            "rand" => {
                let max = 10u64.pow(n.min(18) as u32);
                format!("{:0width$}", rand::random::<u64>() % max, width = n)
            }
            "rand_hex" => (0..n).map(|_| format!("{:X}", rand::random::<u8>() & 0xF)).collect(),
            "uuid" => uuid::Uuid::new_v4().to_string(),
            _ => caps[0].to_string(),
        }
    })
    .into_owned()
}

// ---------- outbound payload ----------

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct SendRequest {
    pub format: Format,
    pub content: String,
    pub encoding: String,
    pub unescape: bool,
    pub template: bool,
    pub line_ending: LineEnding,
    pub custom_ending_hex: String,
    pub checksum: Checksum,
    pub checksum_big_endian: bool,
}

impl Default for SendRequest {
    fn default() -> Self {
        Self {
            format: Format::Text,
            content: String::new(),
            encoding: "utf-8".into(),
            unescape: false,
            template: true,
            line_ending: LineEnding::None,
            custom_ending_hex: String::new(),
            checksum: Checksum::None,
            checksum_big_endian: true,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Payload {
    pub bytes: Vec<u8>,
    pub hex: String,
    pub len: usize,
    /// Offset where the checksum starts, if any.
    pub checksum_offset: Option<usize>,
}

pub fn build_payload(req: &SendRequest) -> Result<Payload, String> {
    let content = if req.template { expand_template(&req.content) } else { req.content.clone() };
    let mut bytes = match req.format {
        Format::Text => {
            let t = if req.unescape { unescape(&content) } else { content };
            encode_text(&t, &req.encoding)
        }
        Format::Hex => parse_hex(&content)?,
        Format::Base64 => base64::engine::general_purpose::STANDARD
            .decode(content.trim())
            .map_err(|e| format!("Base64 解析失败: {e}"))?,
        Format::Json => {
            let v: serde_json::Value = serde_json::from_str(&content).map_err(|e| format!("JSON 无效: {e}"))?;
            encode_text(&serde_json::to_string(&v).unwrap(), &req.encoding)
        }
    };
    let ending: Vec<u8> = match req.line_ending {
        LineEnding::None => vec![],
        LineEnding::Lf => b"\n".to_vec(),
        LineEnding::CrLf => b"\r\n".to_vec(),
        LineEnding::Cr => b"\r".to_vec(),
        LineEnding::Nul => vec![0],
        LineEnding::Custom => parse_hex(&req.custom_ending_hex)?,
    };
    bytes.extend_from_slice(&ending);
    let checksum_offset = if req.checksum != Checksum::None {
        let sum = checksum(&bytes, req.checksum, req.checksum_big_endian);
        let off = bytes.len();
        bytes.extend_from_slice(&sum);
        Some(off)
    } else {
        None
    };
    Ok(Payload { hex: to_hex(&bytes), len: bytes.len(), checksum_offset, bytes })
}

// ---------- inbound display ----------

/// Decide whether bytes are displayable as text in the given encoding.
pub fn classify(bytes: &[u8], encoding: &str) -> (bool, String) {
    let (text, had_errors) = decode_text(bytes, encoding);
    if had_errors || bytes.is_empty() {
        return (false, text);
    }
    let printable = text.chars().all(|c| !c.is_control() || matches!(c, '\n' | '\r' | '\t'));
    (printable, text)
}

pub fn to_base64(bytes: &[u8]) -> String {
    base64::engine::general_purpose::STANDARD.encode(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hex_roundtrip() {
        assert_eq!(parse_hex("48 65 6c 6C").unwrap(), b"Hell");
        assert_eq!(parse_hex("0x48,0x65\n6c").unwrap(), b"Hel");
        assert!(parse_hex("4").is_err());
        assert!(parse_hex("zz").is_err());
        assert_eq!(to_hex(b"Hi"), "48 69");
    }

    #[test]
    fn hexdump_shape() {
        let d = hexdump(b"Hello, world! 123");
        assert!(d.starts_with("00000000  48 65 6C 6C 6F 2C 20 77  6F 72 6C 64 21 20 31 32  Hello, world! 12"));
        assert!(d.contains("\n00000010  33"));
    }

    #[test]
    fn unescape_basic() {
        assert_eq!(unescape(r"a\r\nb\x41中\\"), "a\r\nbA中\\");
    }

    #[test]
    fn crc_modbus() {
        assert_eq!(checksum(&[0x01, 0x03, 0x00, 0x00, 0x00, 0x0A], Checksum::Crc16Modbus, false), vec![0xC5, 0xCD]);
    }

    #[test]
    fn template_expands() {
        let s = expand_template("{{seq}}-{{rand:3}}-{{uuid}}");
        let parts: Vec<&str> = s.splitn(3, '-').collect();
        assert!(parts[0].parse::<u64>().is_ok());
        assert_eq!(parts[1].len(), 3);
        assert_eq!(parts[2].len(), 36);
    }

    #[test]
    fn classify_text_vs_binary() {
        assert!(classify("你好\n".as_bytes(), "utf-8").0);
        assert!(!classify(&[0x01, 0x03, 0xC5, 0xCD], "utf-8").0);
        assert!(classify(&encode_text("中文", "gbk"), "gbk").0);
    }

    #[test]
    fn payload_with_checksum() {
        let req = SendRequest { format: Format::Hex, content: "01 03 00 00 00 0A".into(), checksum: Checksum::Crc16Modbus, checksum_big_endian: false, ..Default::default() };
        let p = build_payload(&req).unwrap();
        assert_eq!(p.hex, "01 03 00 00 00 0A C5 CD");
        assert_eq!(p.checksum_offset, Some(6));
    }
}
