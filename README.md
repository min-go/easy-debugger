<div align="center">

# ⚡ Easy Debugger

**English** · [中文](README.zh.md)

A lightweight desktop **socket debugger** for **TCP, UDP and WebSocket** — client and server —
with built-in **DNS lookup** and **network diagnostics**.
Built with Tauri 2, Rust and Vue 3. Clean UI, tiny install size.

[![Release](https://img.shields.io/github/v/release/min-go/easy-debugger?style=flat-square&color=3E63DD)](https://github.com/min-go/easy-debugger/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/min-go/easy-debugger/total?style=flat-square)](https://github.com/min-go/easy-debugger/releases)
[![License](https://img.shields.io/github/license/min-go/easy-debugger?style=flat-square)](LICENSE)
![Platform](https://img.shields.io/badge/platform-macOS%20%C2%B7%20Windows%20%C2%B7%20Linux%20%C2%B7%20Android-555?style=flat-square)

![Tauri](https://img.shields.io/badge/Tauri-2-24C8DB?style=flat-square&logo=tauri&logoColor=white)
![Rust](https://img.shields.io/badge/Rust-000?style=flat-square&logo=rust&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-4FC08D?style=flat-square&logo=vuedotjs&logoColor=white)

### [⬇ Download the latest release](https://github.com/min-go/easy-debugger/releases/latest)

![Demo](docs/images/en/demo.gif)

</div>

## ✨ Features

| | |
|---|---|
| **Connection modes** | TCP client / server, UDP (unicast · broadcast · multicast), WebSocket client / server — many sessions in parallel |
| **Message formats** | Text (multiple encodings), Hex, Base64, JSON; line-ending append, CRC / XOR / SUM checksum, template variables, pre-send byte preview |
| **Receive display** | Auto · Text · Hex · Hex Dump · Base64, switchable per message; millisecond timestamp, byte count, peer address |
| **TCP framing** | None, delimiter, length prefix, fixed length, timeout aggregation |
| **Conversational UX** | Bubble message stream, snippet library, timed send, auto-reply rules (prefix / contains / regex / hex-prefix → reply / echo / disconnect) |
| **Server side** | Multi-client sessions, targeted send and broadcast, kick peers, live statistics |
| **DNS tool** | A / AAAA / CNAME / MX / TXT / NS / SOA / SRV / PTR, custom and multi-server comparison, reverse lookup |
| **Diagnostics** | Local interfaces, port connectivity check |
| **Experience** | Light / dark theme follows the system, bilingual UI (System / 中文 / English), config persisted as JSON |

## 📸 Screenshots

### Sessions (TCP server, light & dark)

![Session light](docs/images/en/session-light.png)

![Session dark](docs/images/en/session-dark.png)

### DNS tool

![DNS tool](docs/images/en/dns.png)

### Network diagnostics

![Diagnostics](docs/images/en/diagnostics.png)

### Mobile (Android / iOS)

Single-column layout with a bottom tab bar; the info panel becomes a bottom sheet.

<p>
  <img src="docs/images/en/mobile-list.png" width="240" alt="Mobile session list" />
  <img src="docs/images/en/mobile-chat.png" width="240" alt="Mobile session" />
  <img src="docs/images/en/mobile-panel.png" width="240" alt="Mobile info panel" />
</p>

## 📦 Install

Grab the installer for your platform from the [latest release](https://github.com/min-go/easy-debugger/releases/latest):

| Platform | File |
|---|---|
| macOS (Apple Silicon) | `Easy Debugger_*_aarch64.dmg` |
| macOS (Intel) | `Easy Debugger_*_x64.dmg` |
| Windows | `_x64-setup.exe` or `_x64_en-US.msi` |
| Linux | `_amd64.deb` · `.x86_64.rpm` · `_amd64.AppImage` |
| Android | `easy-debugger_*_android.apk` |

The macOS and Windows builds are unsigned. On macOS, if you see *"app is damaged"*, move it to `/Applications` and run:

```bash
xattr -cr "/Applications/Easy Debugger.app"
codesign --force --deep --sign - "/Applications/Easy Debugger.app"
```

On Windows, click **More info → Run anyway** on the SmartScreen prompt.

## 🛠 Development

Requires Node 22+, pnpm, Rust 1.90+; on macOS the Xcode Command Line Tools.

```bash
pnpm install
pnpm tauri dev      # run in dev
pnpm tauri build    # bundle → src-tauri/target/release/bundle
```

## 📱 Mobile

The layout switches automatically by platform: three columns on desktop, a single column with a bottom tab bar and a bottom-sheet info panel on mobile. The core Rust logic is shared across both.

```bash
pnpm tauri android init && pnpm tauri android build   # needs Android Studio + NDK
pnpm tauri ios init && pnpm tauri ios build           # needs macOS + Xcode
```

## 🧪 Testing

```bash
cd src-tauri && cargo test   # unit tests + end-to-end tests for all five transports
pnpm vue-tsc --noEmit        # frontend type check
```

## 🗂 Project layout

- `src/` — frontend: `api/` command & event wrappers, `stores/` state, `components/` & `views/` UI, `i18n/` locales.
- `src-tauri/src/` — backend: `session/` transports, `codec.rs` encode/decode, `framing.rs` framing, `dns.rs` & `net.rs` tools, `commands.rs` command layer.
- `docs/SPEC.md` — full product spec (Chinese).

## 📄 License

[Apache License 2.0](LICENSE)
