//! End-to-end transport tests: real sockets, events captured through a channel emitter.

use super::*;
use crate::config::{AutoReply, Format, Kind, MatchKind, ReplyAction, ReplyRule, SessionConfig, TimedSend};
use crate::events::Emitter;
use std::time::Duration;
use tokio::sync::Mutex as AMutex;

struct Harness {
    handle: Emitter,
    rx: AMutex<mpsc::UnboundedReceiver<serde_json::Value>>,
    /// Every event received, plus whether an assertion has already claimed it.
    buf: AMutex<Vec<(serde_json::Value, bool)>>,
}

fn harness() -> Harness {
    let (tx, rx) = mpsc::unbounded_channel();
    let handle: Emitter = Arc::new(move |ev| {
        let _ = tx.send(serde_json::to_value(&ev).unwrap());
    });
    Harness { handle, rx: AMutex::new(rx), buf: AMutex::new(vec![]) }
}

impl Harness {
    /// Wait until some (as-yet-unclaimed) event matches `pred`, scanning both events already
    /// received and new ones. Order of assertions therefore does not matter, and TCP coalescing
    /// or auto-reply task scheduling cannot make a present event unmatchable.
    async fn wait(&self, pred: impl Fn(&serde_json::Value) -> bool) -> serde_json::Value {
        let deadline = tokio::time::Instant::now() + Duration::from_secs(3);
        loop {
            {
                let mut buf = self.buf.lock().await;
                if let Some(slot) = buf.iter_mut().find(|(v, claimed)| !*claimed && pred(v)) {
                    slot.1 = true;
                    return slot.0.clone();
                }
            }
            let mut rx = self.rx.lock().await;
            match tokio::time::timeout_at(deadline, rx.recv()).await {
                Ok(Some(v)) => self.buf.lock().await.push((v, false)),
                _ => {
                    let seen: Vec<String> = self.buf.lock().await.iter().map(|(v, _)| v.to_string()).collect();
                    panic!("timed out waiting for event; seen so far:\n{}", seen.join("\n"));
                }
            }
        }
    }
    /// Wait until the whole received buffer satisfies `pred` (nothing is claimed).
    async fn wait_all(&self, pred: impl Fn(&[serde_json::Value]) -> bool) {
        let deadline = tokio::time::Instant::now() + Duration::from_secs(3);
        loop {
            {
                let buf = self.buf.lock().await;
                let vals: Vec<serde_json::Value> = buf.iter().map(|(v, _)| v.clone()).collect();
                if pred(&vals) {
                    return;
                }
            }
            let mut rx = self.rx.lock().await;
            match tokio::time::timeout_at(deadline, rx.recv()).await {
                Ok(Some(v)) => self.buf.lock().await.push((v, false)),
                _ => {
                    let seen: Vec<String> = self.buf.lock().await.iter().map(|(v, _)| v.to_string()).collect();
                    panic!("timed out; seen so far:\n{}", seen.join("\n"));
                }
            }
        }
    }
    async fn wait_type(&self, uid: &str, t: &str) -> serde_json::Value {
        self.wait(|v| v["uid"] == uid && v["type"] == t).await
    }
    /// Pull every event currently queued (without blocking) into the buffer, so assertions can
    /// see events that arrived after the last wait() call.
    async fn drain(&self) {
        let mut rx = self.rx.lock().await;
        while let Ok(v) = rx.try_recv() {
            self.buf.lock().await.push((v, false));
        }
    }
    async fn wait_msg(&self, uid: &str, dir: &str, hex: &str) -> serde_json::Value {
        self.wait(|v| v["uid"] == uid && v["type"] == "message" && v["direction"] == dir && v["hex"] == hex).await
    }
}

fn cfg(uid: &str, kind: Kind, host: &str, port: u16) -> SessionConfig {
    SessionConfig { uid: uid.into(), name: uid.into(), kind, host: host.into(), port, ..Default::default() }
}

fn free_port() -> u16 {
    std::net::TcpListener::bind("127.0.0.1:0").unwrap().local_addr().unwrap().port()
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_server_roundtrip_with_auto_reply_and_framing() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();

    let mut server = cfg("srv", Kind::TcpServer, "127.0.0.1", port);
    server.framing.mode = crate::config::FramingMode::Delimiter;
    server.framing.delimiter_hex = "0A".into();
    server.auto_reply = AutoReply {
        enabled: true,
        delay_ms: 0,
        rules: vec![ReplyRule { enabled: true, match_kind: MatchKind::Prefix, pattern: "ping".into(), action: ReplyAction::Reply, format: Format::Text, reply: "pong\n".into() }],
        default_enabled: true,
        default_format: Format::Hex,
        default_reply: "DE AD".into(),
    };
    m.start(h.handle.clone(), server).await.unwrap();
    h.wait_type("srv", "online").await;

    let client = cfg("cli", Kind::TcpClient, "127.0.0.1", port);
    m.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("cli", "online").await;
    let peer = h.wait_type("srv", "peerOnline").await["peer"].as_str().unwrap().to_string();

    // client -> server, two frames in one write; server splits on \n and auto-replies to each
    m.get("cli").unwrap().send(Outbound { target: None, bytes: b"ping 1\nhello\n".to_vec(), as_text: true, disconnect: false }).await.unwrap();
    h.wait_msg("cli", "out", "70 69 6E 67 20 31 0A 68 65 6C 6C 6F 0A").await;
    let in1 = h.wait_msg("srv", "in", "70 69 6E 67 20 31 0A").await;
    assert_eq!(in1["peer"], peer);
    assert_eq!(in1["text"], "ping 1\n");
    h.wait_msg("srv", "in", "68 65 6C 6C 6F 0A").await;
    // The two auto-replies (rule "pong\n" and default "DE AD") may arrive in either order and,
    // since the client has no framing, TCP may coalesce them into one read or split them. Assert
    // both byte sequences reach the client across all its inbound data.
    h.wait_all(|evs| {
        let joined: String = evs.iter().filter(|v| v["uid"] == "cli" && v["type"] == "message" && v["direction"] == "in").filter_map(|v| v["hex"].as_str()).collect::<Vec<_>>().join(" ");
        joined.contains("70 6F 6E 67 0A") && joined.contains("DE AD")
    }).await;

    // server -> specific peer
    m.get("srv").unwrap().send(Outbound { target: Some(peer.clone()), bytes: b"hi".to_vec(), as_text: true, disconnect: false }).await.unwrap();
    h.wait_msg("cli", "in", "68 69").await;

    // status reflects peer
    let st = m.get("srv").unwrap().status().await;
    assert!(st.online && st.peers.len() == 1 && st.peers[0].online);

    // kick -> client sees offline, server sees peerOffline
    m.get("srv").unwrap().kick(&peer).await.unwrap();
    h.wait_type("cli", "offline").await;
    h.wait_type("srv", "peerOffline").await;

    m.stop_all().await;
    h.wait_type("srv", "offline").await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_connect_refused_is_immediate_error() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let err = m.start(h.handle.clone(), cfg("cli", Kind::TcpClient, "127.0.0.1", port)).await.unwrap_err();
    assert!(err.contains("连接失败"), "{err}");
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_timed_send_and_length_prefix() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let mut server = cfg("srv", Kind::TcpServer, "127.0.0.1", port);
    server.framing.mode = crate::config::FramingMode::LengthPrefix;
    server.framing.len_offset = 0;
    server.framing.len_size = 1;
    m.start(h.handle.clone(), server).await.unwrap();
    h.wait_type("srv", "online").await;

    let mut client = cfg("cli", Kind::TcpClient, "127.0.0.1", port);
    client.timed_send = TimedSend { enabled: true, interval_ms: 30, count: 2, format: Format::Hex, content: "02 AA BB".into() };
    m.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("cli", "online").await;
    h.wait_msg("srv", "in", "02 AA BB").await;
    h.wait_msg("srv", "in", "02 AA BB").await;
    h.wait(|v| v["uid"] == "cli" && v["type"] == "info").await;

    // partial frames are reassembled
    m.get("cli").unwrap().send(Outbound { target: None, bytes: vec![0x03, 0x01], as_text: false, disconnect: false }).await.unwrap();
    m.get("cli").unwrap().send(Outbound { target: None, bytes: vec![0x02, 0x03, 0x01, 0x09], as_text: false, disconnect: false }).await.unwrap();
    h.wait_msg("srv", "in", "03 01 02 03").await;
    h.wait_msg("srv", "in", "01 09").await;
    m.stop_all().await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn udp_roundtrip() {
    let h = harness();
    let m = Manager::default();
    let pa = free_port();
    let pb = free_port();
    let mut a = cfg("a", Kind::Udp, "127.0.0.1", pb);
    a.local_port = pa;
    let mut b = cfg("b", Kind::Udp, "127.0.0.1", pa);
    b.local_port = pb;
    m.start(h.handle.clone(), a).await.unwrap();
    m.start(h.handle.clone(), b).await.unwrap();
    h.wait_type("a", "online").await;
    h.wait_type("b", "online").await;
    m.get("a").unwrap().send(Outbound { target: None, bytes: b"udp!".to_vec(), as_text: true, disconnect: false }).await.unwrap();
    let got = h.wait_msg("b", "in", "75 64 70 21").await;
    assert_eq!(got["peer"], format!("127.0.0.1:{pa}"));
    h.wait_type("b", "peerOnline").await;
    // reply to that specific peer
    m.get("b").unwrap().send(Outbound { target: Some(format!("127.0.0.1:{pa}")), bytes: b"ok".to_vec(), as_text: true, disconnect: false }).await.unwrap();
    h.wait_msg("a", "in", "6F 6B").await;
    m.stop_all().await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn websocket_client_server_text_and_binary() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let mut server = cfg("ws", Kind::WsServer, "127.0.0.1", port);
    server.ws_path = "/ws".into();
    server.auto_reply = AutoReply { enabled: true, rules: vec![ReplyRule { enabled: true, match_kind: MatchKind::Exact, pattern: "echo me".into(), action: ReplyAction::Echo, ..Default::default() }], ..Default::default() };
    m.start(h.handle.clone(), server).await.unwrap();
    h.wait_type("ws", "online").await;

    let mut client = cfg("wc", Kind::WsClient, "127.0.0.1", port);
    client.ws_path = "/ws".into();
    client.ws_headers = vec![("X-Test".into(), "1".into())];
    m.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("wc", "online").await;
    h.wait_type("ws", "peerOnline").await;

    m.get("wc").unwrap().send(Outbound { target: None, bytes: b"echo me".to_vec(), as_text: true, disconnect: false }).await.unwrap();
    h.wait_msg("ws", "in", "65 63 68 6F 20 6D 65").await;
    h.wait_msg("wc", "in", "65 63 68 6F 20 6D 65").await;
    m.get("ws").unwrap().send(Outbound { target: None, bytes: vec![0x00, 0xFF], as_text: false, disconnect: false }).await.unwrap();
    let v = h.wait_msg("wc", "in", "00 FF").await;
    assert_eq!(v["isText"], false);

    m.stop("wc").await;
    h.wait_type("wc", "offline").await;
    h.wait_type("ws", "peerOffline").await;
    m.stop_all().await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_auto_reconnect() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let mut client = cfg("cli", Kind::TcpClient, "127.0.0.1", port);
    client.auto_reconnect = true;
    client.reconnect_interval_ms = 200;
    // no server yet: start succeeds and goes into reconnect loop
    m.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("cli", "error").await;
    h.wait_type("cli", "reconnecting").await;
    let m2 = Manager::default();
    m2.start(h.handle.clone(), cfg("srv", Kind::TcpServer, "127.0.0.1", port)).await.unwrap();
    h.wait_type("cli", "online").await;
    m2.stop_all().await;
    h.wait_type("cli", "offline").await;
    h.wait_type("cli", "reconnecting").await;
    m.stop_all().await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_stop_during_reconnect_notifies_offline() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let mut client = cfg("cli", Kind::TcpClient, "127.0.0.1", port);
    client.auto_reconnect = true;
    client.reconnect_interval_ms = 300;
    m.start(h.handle.clone(), client).await.unwrap();
    // no server: first attempt fails, enters reconnect loop
    h.wait_type("cli", "error").await;
    h.wait_type("cli", "reconnecting").await;
    // stopping while reconnecting must clear the UI state
    m.stop("cli").await;
    h.wait_type("cli", "offline").await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_reconnect_exhausted_notifies_offline() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let mut client = cfg("cli", Kind::TcpClient, "127.0.0.1", port);
    client.auto_reconnect = true;
    client.reconnect_interval_ms = 200;
    client.reconnect_max = 1;
    m.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("cli", "error").await; // first attempt failed
    h.wait_type("cli", "reconnecting").await; // attempt 1
    // attempt 1 also fails -> loop exhausts and must emit offline (not linger as reconnecting)
    h.wait_type("cli", "offline").await;
    m.stop_all().await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn ws_server_handshake_timeout_releases_slot() {
    let h = harness();
    let m = Manager::default();
    let port = free_port();
    let mut server = cfg("ws", Kind::WsServer, "127.0.0.1", port);
    server.max_connections = 1;
    m.start(h.handle.clone(), server).await.unwrap();
    h.wait_type("ws", "online").await;
    // Raw TCP connect with no WebSocket handshake: occupies a slot until it times out.
    let _raw = tokio::net::TcpStream::connect(("127.0.0.1", port)).await.unwrap();
    h.wait_type("ws", "peerOnline").await;
    // The stuck peer must be released (handshake failure/timeout), freeing the slot.
    // A real WS client can then connect within the max_connections=1 limit.
    let started = std::time::Instant::now();
    loop {
        let mut c = cfg("wc", Kind::WsClient, "127.0.0.1", port);
        c.ws_path = "/".into();
        c.connect_timeout_ms = 800;
        let m2 = Manager::default();
        if m2.start(h.handle.clone(), c).await.is_ok() {
            // wait to see if it actually comes online (slot was free)
            let ok = tokio::time::timeout(std::time::Duration::from_secs(2), async {
                h.wait_type("wc", "online").await;
            }).await.is_ok();
            m2.stop_all().await;
            if ok { break; }
        }
        assert!(started.elapsed() < std::time::Duration::from_secs(15), "slot never freed");
        tokio::time::sleep(std::time::Duration::from_millis(300)).await;
    }
    let _ = _raw;
    m.stop_all().await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn tcp_client_stop_after_reconnect_then_drop_notifies_offline() {
    // Regression: reconnect succeeds (serve clears the reconnecting flag), the connection drops
    // again and the loop re-enters retry. Stopping there must still emit offline, not linger.
    let h = harness();
    let cm = Manager::default();
    let port = free_port();

    // First, a server so the client can actually connect once.
    let sm = Manager::default();
    sm.start(h.handle.clone(), cfg("srv", Kind::TcpServer, "127.0.0.1", port)).await.unwrap();
    h.wait_type("srv", "online").await;

    let mut client = cfg("cli", Kind::TcpClient, "127.0.0.1", port);
    client.auto_reconnect = true;
    client.reconnect_interval_ms = 300;
    cm.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("cli", "online").await; // connected; serve() has cleared reconnecting

    // Drop the connection by stopping the server: the client re-enters the retry loop.
    sm.stop_all().await;
    h.wait_type("cli", "offline").await; // the drop itself notifies offline
    h.wait_type("cli", "reconnecting").await; // and the loop is retrying (flag restored)

    // Now stop while in that second retry phase: must emit offline (flag is set again).
    cm.stop("cli").await;
    // Drain until we see the offline that follows the manual stop.
    h.wait(|v| v["uid"] == "cli" && v["type"] == "offline" && v["reason"] == "手动断开").await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn ws_client_stop_during_handshake_stays_offline() {
    // Repro: a stop lands while a *reconnect* handshake is in flight. The server signals the
    // moment it has accepted the TCP connection (before delaying the WS handshake), so the stop
    // deterministically hits mid-handshake. The client must NOT publish online afterwards
    // (offline was already sent by stop), or the UI is stuck "connected" but unable to send.
    let h = harness();
    let cm = Manager::default();
    let port = free_port(); // nothing is listening yet

    let mut client = cfg("cli", Kind::WsClient, "127.0.0.1", port);
    client.ws_path = "/".into();
    client.auto_reconnect = true;
    client.reconnect_interval_ms = 150;
    client.connect_timeout_ms = 5000;
    cm.start(h.handle.clone(), client).await.unwrap();
    h.wait_type("cli", "reconnecting").await;

    // Server: accept TCP, signal "accepted", then delay the WS handshake so it is in flight.
    let (tx_acc, rx_acc) = tokio::sync::oneshot::channel::<()>();
    let listener = tokio::net::TcpListener::bind(("127.0.0.1", port)).await.unwrap();
    tokio::spawn(async move {
        if let Ok((stream, _)) = listener.accept().await {
            let _ = tx_acc.send(());
            tokio::time::sleep(std::time::Duration::from_millis(600)).await;
            let _ = tokio_tungstenite::accept_async(stream).await; // late handshake completion
            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
        }
    });

    // Block until the reconnect attempt's TCP connection is accepted → handshake is now in flight.
    rx_acc.await.unwrap();
    cm.stop("cli").await;
    h.wait(|v| v["uid"] == "cli" && v["type"] == "offline" && v["reason"] == "手动断开").await;

    // Let the server finish its delayed handshake; the client must never report online after stop.
    tokio::time::sleep(std::time::Duration::from_millis(1000)).await;
    h.drain().await;
    let onlined = h.buf.lock().await.iter().any(|(v, _)| v["uid"] == "cli" && v["type"] == "online");
    assert!(!onlined, "client published online after stop — stuck-connected race");
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn online_and_offline_are_serialized_under_the_state_lock() {
    // A stop that lands exactly as serve() publishes online must not end up delivering offline
    // *before* online (which would strand the UI connected). We pause delivery of the cli `online`
    // event and, while it is paused, run stop() concurrently. With emission serialized under the
    // state lock, stop cannot deliver offline until online finishes; the observed order is always
    // online→offline. If online were emitted outside the lock, stop's offline would slip in first.
    use std::sync::{Arc as SArc, Condvar, Mutex as SMutex};

    let log: SArc<SMutex<Vec<String>>> = SArc::default();
    let started: SArc<(SMutex<bool>, Condvar)> = SArc::new((SMutex::new(false), Condvar::new()));
    let release: SArc<(SMutex<bool>, Condvar)> = SArc::new((SMutex::new(false), Condvar::new()));
    let (log2, started2, release2) = (log.clone(), started.clone(), release.clone());

    let emitter: crate::events::Emitter = Arc::new(move |ev| {
        let v = serde_json::to_value(&ev).unwrap();
        if v["uid"] != "cli" {
            return;
        }
        match v["type"].as_str().unwrap_or("") {
            "online" => {
                { let (m, c) = &*started2; *m.lock().unwrap() = true; c.notify_all(); }
                { let (m, c) = &*release2; let mut g = m.lock().unwrap(); while !*g { g = c.wait(g).unwrap(); } }
                log2.lock().unwrap().push("online".into());
            }
            "offline" => log2.lock().unwrap().push("offline".into()),
            _ => {}
        }
    });

    // A server that accepts and holds connections, so the client connects immediately.
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    tokio::spawn(async move {
        let mut held = vec![];
        while let Ok((s, _)) = listener.accept().await { held.push(s); }
    });

    let mgr = Arc::new(Manager::default());
    mgr.start(emitter, cfg("cli", Kind::TcpClient, "127.0.0.1", port)).await.unwrap();

    // Wait until serve() has entered the (paused) online delivery.
    {
        let started = started.clone();
        tokio::task::spawn_blocking(move || {
            let (m, c) = &*started;
            let mut g = m.lock().unwrap();
            while !*g { g = c.wait(g).unwrap(); }
        }).await.unwrap();
    }

    // Stop concurrently while online delivery is paused.
    let stop_task = { let m = mgr.clone(); tokio::spawn(async move { m.stop("cli").await; }) };

    // Give a stop that ISN'T serialized time to deliver offline first.
    tokio::time::sleep(std::time::Duration::from_millis(300)).await;

    // Release online delivery.
    { let (m, c) = &*release; *m.lock().unwrap() = true; c.notify_all(); }
    stop_task.await.unwrap();

    let order = log.lock().unwrap().clone();
    assert_eq!(order, vec!["online".to_string(), "offline".to_string()], "offline was delivered before online — UI stuck connected");
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn stop_always_notifies_offline_even_racing_serve_cleanup() {
    // The dangerous window is between serve cleanup clearing `online` and its (old, out-of-lock)
    // cancel check: if stop cancels there, cleanup skips offline (now cancelled) and stop skips too
    // (online already cleared) — the offline is dropped and the UI stays connected. The server
    // signals the moment it closes so the test calls stop() aligned with serve's cleanup.
    for i in 0..400 {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (tx_close, rx_close) = tokio::sync::oneshot::channel::<()>();
        tokio::spawn(async move {
            if let Ok((c, _)) = listener.accept().await {
                let _ = tx_close.send(()); // signal, then close immediately
                drop(c);
            }
        });
        let h = harness();
        let m = Manager::default();
        let uid = format!("c{i}");
        m.start(h.handle.clone(), cfg(&uid, Kind::TcpClient, "127.0.0.1", port)).await.unwrap();
        h.wait_type(&uid, "online").await;
        // Align stop with the server-side close so serve cleanup and stop race for the notification.
        let _ = rx_close.await;
        m.stop(&uid).await;
        let uid2 = uid.clone();
        let got = tokio::time::timeout(std::time::Duration::from_secs(2), h.wait(move |v| v["uid"] == uid2 && v["type"] == "offline")).await;
        assert!(got.is_ok(), "iteration {i}: offline was dropped after stop");
    }
}


