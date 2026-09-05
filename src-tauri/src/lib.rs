mod codec;
mod commands;
mod config;
mod dns;
mod events;
mod framing;
mod net;
mod session;

use tauri::Manager;

pub struct AppState {
    pub store: config::Store,
    pub manager: session::Manager,
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
    // rustls 0.23 needs an explicit crypto provider; install it before any wss:// TLS handshake.
    let _ = rustls::crypto::ring::default_provider().install_default();
    tauri::Builder::default()
        .plugin(tauri_plugin_os::init())
        .setup(|app| {
            let dir = app.path().app_config_dir().expect("app config dir");
            app.manage(AppState { store: config::Store::open(dir), manager: session::Manager::default() });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::list_sessions,
            commands::save_session,
            commands::delete_sessions,
            commands::reorder_sessions,
            commands::start_session,
            commands::stop_session,
            commands::session_status,
            commands::running_sessions,
            commands::send_message,
            commands::preview_payload,
            commands::kick_peer,
            commands::hexdump,
            commands::decode_bytes,
            commands::to_base64,
            commands::list_snippets,
            commands::save_snippet,
            commands::delete_snippets,
            commands::get_settings,
            commands::save_settings,
            commands::config_dir,
            commands::dns_query,
            commands::resolve_host,
            commands::list_interfaces,
            commands::check_ports,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
