import { defineStore } from 'pinia';
import { reactive, ref } from 'vue';
import { api } from '../api/commands';
import { t } from '../i18n';
import type { Message, PeerInfo, SessionEvent } from '../types';

export interface Runtime {
  online: boolean;
  reconnecting: number;
  local: string | null;
  peers: PeerInfo[];
  messages: Message[];
  unread: number;
  unreadByPeer: Record<string, number>;
  bytesIn: number;
  bytesOut: number;
  countIn: number;
  countOut: number;
  errors: number;
  onlineSince: number | null;
  totalPeers: number;
}

let nextId = 1;
export const MAX_MESSAGES = ref(5000);

function blank(): Runtime {
  return { online: false, reconnecting: 0, local: null, peers: [], messages: [], unread: 0, unreadByPeer: {}, bytesIn: 0, bytesOut: 0, countIn: 0, countOut: 0, errors: 0, onlineSince: null, totalPeers: 0 };
}

export const useSessions = defineStore('sessions', () => {
  const runtime = reactive<Record<string, Runtime>>({});
  const active = ref<string | null>(null);
  const activePeer = ref<string | null>(null);

  function rt(uid: string): Runtime {
    if (!runtime[uid]) runtime[uid] = blank();
    return runtime[uid];
  }

  function push(uid: string, m: Omit<Message, 'id'>) {
    const r = rt(uid);
    r.messages.push({ ...m, id: nextId++ });
    if (r.messages.length > MAX_MESSAGES.value) r.messages.splice(0, r.messages.length - MAX_MESSAGES.value);
  }

  function sys(uid: string, text: string, level: 'info' | 'error' = 'info') {
    push(uid, { kind: 'sys', peer: null, direction: 'in', hex: '', text, isText: true, len: 0, ts: Date.now(), level });
  }

  function upsertPeer(uid: string, addr: string, online: boolean) {
    const r = rt(uid);
    const p = r.peers.find((x) => x.addr === addr);
    if (p) {
      if (online && !p.online) p.since = Date.now();
      p.online = online;
    } else {
      r.peers.push({ addr, online, since: Date.now() });
      r.totalPeers++;
    }
  }

  function handle(e: SessionEvent) {
    const r = rt(e.uid);
    const isActive = active.value === e.uid;
    switch (e.type) {
      case 'online':
        r.online = true;
        r.reconnecting = 0;
        r.local = e.local;
        r.onlineSince = Date.now();
        sys(e.uid, e.local ? t('event.connectedLocal', { local: e.local }) : t('event.connected'));
        break;
      case 'offline':
        r.online = false;
        r.reconnecting = 0;
        r.onlineSince = null;
        r.peers.forEach((p) => (p.online = false));
        sys(e.uid, e.reason ? t('event.disconnectedReason', { reason: e.reason }) : t('event.disconnected'));
        break;
      case 'reconnecting':
        r.reconnecting = e.attempt;
        sys(e.uid, t('event.reconnecting', { n: e.attempt }));
        break;
      case 'peerOnline':
        upsertPeer(e.uid, e.peer, true);
        sys(e.uid, t('event.peerJoined', { peer: e.peer }));
        break;
      case 'peerOffline':
        upsertPeer(e.uid, e.peer, false);
        sys(e.uid, e.reason ? t('event.peerLeftReason', { peer: e.peer, reason: e.reason }) : t('event.peerLeft', { peer: e.peer }));
        break;
      case 'message':
        push(e.uid, { kind: 'msg', peer: e.peer, direction: e.direction, hex: e.hex, text: e.text, isText: e.isText, len: e.len, ts: e.ts });
        if (e.direction === 'in') {
          r.bytesIn += e.len;
          r.countIn++;
          if (!isActive) r.unread++;
          if (e.peer && (!isActive || activePeer.value !== e.peer)) r.unreadByPeer[e.peer] = (r.unreadByPeer[e.peer] ?? 0) + 1;
        } else {
          r.bytesOut += e.len;
          r.countOut++;
        }
        break;
      case 'error':
        r.errors++;
        sys(e.uid, e.message, 'error');
        break;
      case 'info':
        sys(e.uid, e.message);
        break;
    }
  }

  async function sync(uid: string) {
    const s = await api.sessionStatus(uid);
    const r = rt(uid);
    r.online = s.online;
    r.local = s.local;
    for (const p of s.peers) {
      const cur = r.peers.find((x) => x.addr === p.addr);
      if (cur) cur.online = p.online;
      else r.peers.push({ ...p });
    }
  }

  function select(uid: string | null) {
    active.value = uid;
    activePeer.value = null;
    if (uid) {
      rt(uid).unread = 0;
      void sync(uid);
    }
  }

  function selectPeer(addr: string | null) {
    activePeer.value = addr;
    if (active.value && addr) delete rt(active.value).unreadByPeer[addr];
  }

  function clear(uid: string) {
    const r = rt(uid);
    r.messages = [];
    r.bytesIn = r.bytesOut = r.countIn = r.countOut = r.errors = 0;
  }

  function drop(uid: string) {
    delete runtime[uid];
    if (active.value === uid) active.value = null;
  }

  return { runtime, active, activePeer, rt, handle, sync, select, selectPeer, clear, drop, sys };
});
