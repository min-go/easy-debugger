// Mirrors the Rust models in src-tauri/src/config.rs, codec.rs and events.rs (camelCase serde).

export type Kind = 'tcpClient' | 'tcpServer' | 'udp' | 'wsClient' | 'wsServer';
export type Format = 'text' | 'hex' | 'base64' | 'json';
export type LineEnding = 'none' | 'lf' | 'crLf' | 'cr' | 'nul' | 'custom';
export type Checksum = 'none' | 'crc16Modbus' | 'crc16Ccitt' | 'crc32' | 'xor' | 'sum8';
export type FramingMode = 'none' | 'delimiter' | 'lengthPrefix' | 'fixed' | 'timeout';
export type MatchKind = 'exact' | 'prefix' | 'contains' | 'regex' | 'hexPrefix';
export type ReplyAction = 'reply' | 'echo' | 'disconnect';

export interface Framing {
  mode: FramingMode;
  delimiterHex: string;
  lenOffset: number;
  lenSize: number;
  bigEndian: boolean;
  lenIncludesHeader: boolean;
  fixedLen: number;
  timeoutMs: number;
}

export interface ReplyRule {
  enabled: boolean;
  matchKind: MatchKind;
  pattern: string;
  action: ReplyAction;
  format: Format;
  reply: string;
}

export interface AutoReply {
  enabled: boolean;
  delayMs: number;
  rules: ReplyRule[];
  defaultEnabled: boolean;
  defaultFormat: Format;
  defaultReply: string;
}

export interface TimedSend {
  enabled: boolean;
  intervalMs: number;
  count: number;
  format: Format;
  content: string;
}

export interface SessionConfig {
  uid: string;
  name: string;
  kind: Kind;
  host: string;
  port: number;
  group: string;
  connectTimeoutMs: number;
  autoReconnect: boolean;
  reconnectIntervalMs: number;
  reconnectMax: number;
  localBind: string;
  nodelay: boolean;
  keepalive: boolean;
  maxConnections: number;
  localPort: number;
  broadcast: boolean;
  multicastGroup: string;
  multicastTtl: number;
  wsPath: string;
  wsHeaders: [string, string][];
  sendEncoding: string;
  recvEncoding: string;
  framing: Framing;
  autoReply: AutoReply;
  timedSend: TimedSend;
}

export interface Snippet {
  uid: string;
  name: string;
  group: string;
  format: Format;
  content: string;
}

export interface AppSettings {
  theme: 'system' | 'light' | 'dark';
  language: 'system' | 'zh' | 'en';
  maxMessages: number;
  fontSize: number;
  restoreSessions: boolean;
}

export interface SendRequest {
  format: Format;
  content: string;
  encoding: string;
  unescape: boolean;
  template: boolean;
  lineEnding: LineEnding;
  customEndingHex: string;
  checksum: Checksum;
  checksumBigEndian: boolean;
}

export interface Payload {
  bytes: number[];
  hex: string;
  len: number;
  checksumOffset: number | null;
}

export interface PeerInfo {
  addr: string;
  online: boolean;
  since: number;
}

export interface Status {
  online: boolean;
  local: string | null;
  peers: PeerInfo[];
}

export type Direction = 'in' | 'out';

export type SessionEvent =
  | { type: 'online'; uid: string; local: string | null }
  | { type: 'offline'; uid: string; reason: string | null }
  | { type: 'reconnecting'; uid: string; attempt: number }
  | { type: 'peerOnline'; uid: string; peer: string }
  | { type: 'peerOffline'; uid: string; peer: string; reason: string | null }
  | { type: 'message'; uid: string; peer: string | null; direction: Direction; hex: string; text: string; isText: boolean; len: number; ts: number }
  | { type: 'error'; uid: string; message: string }
  | { type: 'info'; uid: string; message: string };

export interface DnsRecord {
  name: string;
  recordType: string;
  value: string;
  ttl: number;
}

export interface DnsResult {
  query: string;
  recordType: string;
  server: string;
  elapsedMs: number;
  records: DnsRecord[];
}

export interface Resolved {
  ip: string;
  recordType: string;
  elapsedMs: number;
}

export interface Interface {
  name: string;
  ip: string;
  isIpv6: boolean;
  isLoopback: boolean;
}

export interface PortCheck {
  port: number;
  open: boolean;
  elapsedMs: number;
  error: string | null;
}

/** One row in the message stream (UI-side). */
export interface Message {
  id: number;
  kind: 'msg' | 'sys';
  peer: string | null;
  direction: Direction;
  hex: string;
  text: string;
  isText: boolean;
  len: number;
  ts: number;
  level?: 'info' | 'error';
}

/** i18n keys; render with t(KIND_LABEL[kind]). */
export const KIND_LABEL: Record<Kind, string> = {
  tcpClient: 'kind.tcpClient',
  tcpServer: 'kind.tcpServer',
  udp: 'kind.udp',
  wsClient: 'kind.wsClient',
  wsServer: 'kind.wsServer',
};

export const KIND_GROUPS: { kind: Kind }[] = [
  { kind: 'tcpClient' }, { kind: 'tcpServer' }, { kind: 'udp' }, { kind: 'wsClient' }, { kind: 'wsServer' },
];

export const ENCODINGS = ['utf-8', 'gbk', 'gb18030', 'ascii', 'latin-1', 'utf-16le', 'utf-16be'];

export function isServer(kind: Kind): boolean {
  return kind === 'tcpServer' || kind === 'wsServer';
}

export function defaultConfig(kind: Kind): SessionConfig {
  return {
    uid: '',
    name: '',
    kind,
    host: isServer(kind) ? '0.0.0.0' : '127.0.0.1',
    port: kind === 'wsClient' || kind === 'wsServer' ? 8080 : kind === 'udp' ? 9000 : 8080,
    group: '',
    connectTimeoutMs: 5000,
    autoReconnect: false,
    reconnectIntervalMs: 3000,
    reconnectMax: 0,
    localBind: '',
    nodelay: true,
    keepalive: true,
    maxConnections: 0,
    localPort: kind === 'udp' ? 9000 : 0,
    broadcast: false,
    multicastGroup: '',
    multicastTtl: 1,
    wsPath: '/',
    wsHeaders: [],
    sendEncoding: 'utf-8',
    recvEncoding: 'utf-8',
    framing: { mode: 'none', delimiterHex: '0A', lenOffset: 0, lenSize: 2, bigEndian: true, lenIncludesHeader: false, fixedLen: 8, timeoutMs: 50 },
    autoReply: { enabled: false, delayMs: 0, rules: [], defaultEnabled: false, defaultFormat: 'text', defaultReply: '' },
    timedSend: { enabled: false, intervalMs: 1000, count: 0, format: 'text', content: '' },
  };
}
