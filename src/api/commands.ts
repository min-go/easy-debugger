import { invoke } from '@tauri-apps/api/core';
import type { AppSettings, DnsResult, Interface, Payload, PortCheck, Resolved, SendRequest, SessionConfig, Snippet, Status } from '../types';

export const api = {
  listSessions: () => invoke<SessionConfig[]>('list_sessions'),
  saveSession: (config: SessionConfig) => invoke<SessionConfig>('save_session', { config }),
  deleteSessions: (uids: string[]) => invoke<void>('delete_sessions', { uids }),
  reorderSessions: (uids: string[]) => invoke<void>('reorder_sessions', { uids }),

  startSession: (uid: string) => invoke<void>('start_session', { uid }),
  stopSession: (uid: string) => invoke<void>('stop_session', { uid }),
  sessionStatus: (uid: string) => invoke<Status>('session_status', { uid }),
  runningSessions: () => invoke<string[]>('running_sessions'),
  sendMessage: (uid: string, target: string | null, request: SendRequest) => invoke<Payload>('send_message', { uid, target, request }),
  previewPayload: (request: SendRequest) => invoke<Payload>('preview_payload', { request }),
  kickPeer: (uid: string, peer: string) => invoke<void>('kick_peer', { uid, peer }),
  hexdump: (hex: string) => invoke<string>('hexdump', { hex }),
  decodeBytes: (hex: string, encoding: string) => invoke<string>('decode_bytes', { hex, encoding }),
  toBase64: (hex: string) => invoke<string>('to_base64', { hex }),

  listSnippets: () => invoke<Snippet[]>('list_snippets'),
  saveSnippet: (snippet: Snippet) => invoke<Snippet>('save_snippet', { snippet }),
  deleteSnippets: (uids: string[]) => invoke<void>('delete_snippets', { uids }),
  getSettings: () => invoke<AppSettings>('get_settings'),
  saveSettings: (settings: AppSettings) => invoke<void>('save_settings', { settings }),
  configDir: () => invoke<string>('config_dir'),

  dnsQuery: (name: string, recordType: string, server: string | null) => invoke<DnsResult>('dns_query', { name, recordType, server }),
  resolveHost: (host: string) => invoke<Resolved[]>('resolve_host', { host }),
  listInterfaces: () => invoke<Interface[]>('list_interfaces'),
  checkPorts: (host: string, ports: number[], timeoutMs?: number) => invoke<PortCheck[]>('check_ports', { host, ports, timeoutMs }),
};
