<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMessage } from 'naive-ui';
import Icon from '../components/Icon.vue';
import MessageList from '../components/MessageList.vue';
import SendBar from '../components/SendBar.vue';
import InfoPanel from '../components/InfoPanel.vue';
import ConfigDialog from '../components/ConfigDialog.vue';
import SettingsDialog from '../components/SettingsDialog.vue';
import DnsView from '../views/DnsView.vue';
import DiagView from '../views/DiagView.vue';
import SnippetsView from '../views/SnippetsView.vue';
import { useConfigs } from '../stores/configs';
import { useSessions } from '../stores/sessions';
import { useUi } from '../stores/ui';
import { api } from '../api/commands';
import { KIND_GROUPS, KIND_LABEL, isServer, type Kind, type SessionConfig } from '../types';

const configs = useConfigs();
const sessions = useSessions();
const ui = useUi();
const message = useMessage();
const { t } = useI18n();

type Tab = 'sessions' | 'dns' | 'snippets' | 'settings';
const tab = ref<Tab>('sessions');
const filter = ref('');
const sheetOpen = ref(false);
const dialog = ref<{ open: boolean; config: SessionConfig | null; kind: Kind }>({ open: false, config: null, kind: 'tcpClient' });

const active = computed(() => (sessions.active ? configs.byUid[sessions.active] ?? null : null));
const server = computed(() => (active.value ? isServer(active.value.kind) || active.value.kind === 'udp' : false));

const groups = computed(() =>
  KIND_GROUPS.map((g) => ({ ...g, items: configs.byKind(g.kind).filter((c) => !filter.value || c.name.includes(filter.value) || c.host.includes(filter.value)) })).filter((g) => g.items.length),
);
function stateOf(uid: string) {
  const r = sessions.runtime[uid];
  return r?.online ? 'on' : r?.reconnecting ? 'warn' : '';
}
function addr(c: SessionConfig) {
  if (c.kind === 'udp') return c.host ? `${c.host}:${c.port}` : `${t('common.local')} ${c.localPort || t('common.auto')}`;
  if (c.kind === 'wsClient' && c.host.startsWith('ws')) return c.host;
  return `${c.host}:${c.port}${c.kind.startsWith('ws') && c.wsPath !== '/' ? c.wsPath : ''}`;
}
function open(c: SessionConfig) {
  sessions.select(c.uid);
}
function back() {
  sessions.select(null);
  sheetOpen.value = false;
}
async function toggle() {
  const c = active.value!;
  const r = sessions.rt(c.uid);
  try {
    if (r.online || r.reconnecting) await api.stopSession(c.uid);
    else await api.startSession(c.uid);
  } catch (e) {
    message.error(String(e));
    sessions.sys(c.uid, String(e), 'error');
  }
}
const rt = computed(() => (active.value ? sessions.rt(active.value.uid) : null));
const statusText = computed(() => {
  if (!rt.value) return '';
  if (rt.value.online) return server.value ? t('status.listening') : t('status.connected');
  if (rt.value.reconnecting) return t('status.reconnecting');
  return server.value ? t('status.notListening') : t('status.disconnected');
});
function openNew(kind: Kind = 'tcpClient') {
  dialog.value = { open: true, config: null, kind };
}
function openEdit() {
  if (active.value) dialog.value = { open: true, config: active.value, kind: active.value.kind };
}
const peers = computed(() => rt.value?.peers ?? []);
</script>

<template>
  <div class="m-shell">
    <!-- Session detail (pushed over the list) -->
    <div v-if="tab === 'sessions' && active" class="page chat">
      <header class="m-head">
        <button class="icon-btn" @click="back"><Icon name="chevronR" style="transform:rotate(180deg)" /></button>
        <div class="ttl">
          <div class="r"><span class="name">{{ active.name }}</span><span class="dot" :class="{ on: rt?.online, warn: !!rt?.reconnecting }"></span></div>
          <span class="mono muted sub">{{ statusText }} · {{ addr(active) }}</span>
        </div>
        <button class="icon-btn" @click="sheetOpen = true"><Icon name="panel" /></button>
        <button class="icon-btn" @click="openEdit"><Icon name="edit" /></button>
      </header>
      <div v-if="server && peers.length" class="peerbar">
        <button class="mchip" :class="{ active: !sessions.activePeer }" @click="sessions.selectPeer(null)">{{ t('mobile.allPeers') }}</button>
        <button v-for="p in peers" :key="p.addr" class="mchip" :class="{ active: sessions.activePeer === p.addr, off: !p.online }" @click="sessions.selectPeer(p.addr)">
          <span class="dot" :class="{ on: p.online }" style="width:6px;height:6px;box-shadow:none"></span>{{ p.addr.split(':')[0] }}
          <span v-if="rt?.unreadByPeer[p.addr]" class="mini">{{ rt.unreadByPeer[p.addr] }}</span>
        </button>
      </div>
      <MessageList :uid="active.uid" />
      <SendBar :config="active" />
      <div class="statusrow">
        <button class="btn" :class="rt?.online || rt?.reconnecting ? 'danger' : 'primary'" @click="toggle">
          {{ rt?.online || rt?.reconnecting ? (server ? t('status.closeListen') : t('status.disconnect')) : server ? t('status.startListen') : t('status.connect') }}
        </button>
        <button class="btn ghost" @click="sessions.clear(active.uid)">{{ t('common.clear') }}</button>
      </div>
      <!-- info bottom sheet -->
      <div v-if="sheetOpen" class="backdrop" @click.self="sheetOpen = false">
        <div class="sheet">
          <div class="grip"></div>
          <div class="sheet-body"><InfoPanel :config="active" /></div>
        </div>
      </div>
    </div>

    <!-- Session list -->
    <div v-else-if="tab === 'sessions'" class="page">
      <header class="m-head plain"><span class="big">{{ t('nav.sessions') }}</span><span class="spacer"></span><button class="icon-btn" @click="openNew()"><Icon name="plus" /></button></header>
      <div class="search"><Icon name="search" size="sm" /><input v-model="filter" :placeholder="t('sidebar.filter')" /></div>
      <div class="list">
        <template v-for="g in groups" :key="g.kind">
          <div class="ghead section-title">{{ t(`kind.${g.kind}`) }} <span class="faint">{{ g.items.length }}</span></div>
          <button v-for="c in g.items" :key="c.uid" class="row" @click="open(c)">
            <span class="dot" :class="stateOf(c.uid)"></span>
            <div class="text">
              <div class="r"><span class="name">{{ c.name }}</span><span class="chip" style="height:18px;font-size:10.5px;cursor:default">{{ t(KIND_LABEL[c.kind]) }}</span></div>
              <span class="mono muted sub">{{ addr(c) }}</span>
            </div>
            <span v-if="sessions.runtime[c.uid]?.unread" class="badge">{{ sessions.runtime[c.uid].unread }}</span>
            <Icon name="chevronR" size="sm" class="faint" />
          </button>
        </template>
        <div v-if="!groups.length" class="empty">{{ t('mobile.newBottom') }}</div>
      </div>
    </div>

    <div v-else-if="tab === 'dns'" class="page"><DnsView /></div>
    <div v-else-if="tab === 'snippets'" class="page"><SnippetsView /></div>
    <div v-else class="page settings-page">
      <header class="m-head plain"><span class="big">{{ t('nav.settings') }}</span></header>
      <div class="setlist">
        <button class="setrow" @click="ui.settingsOpen = true"><Icon name="settings" /><span>{{ t('mobile.prefs') }}</span><span class="spacer"></span><Icon name="chevronR" size="sm" class="faint" /></button>
        <div class="setrow static"><Icon name="diag" /><span>{{ t('mobile.networkDiag') }}</span></div>
        <div class="diag-inline"><DiagView /></div>
      </div>
    </div>

    <!-- bottom tab bar: hidden while inside a session chat -->
    <nav v-if="!(tab === 'sessions' && active)" class="tabbar">
      <button class="t" :class="{ active: tab === 'sessions' }" @click="tab = 'sessions'"><Icon name="sessions" /><span>{{ t('nav.sessions') }}</span></button>
      <button class="t" :class="{ active: tab === 'dns' }" @click="tab = 'dns'"><Icon name="dns" /><span>{{ t('nav.dns') }}</span></button>
      <button class="t" :class="{ active: tab === 'snippets' }" @click="tab = 'snippets'"><Icon name="snippets" /><span>{{ t('nav.snippetsShort') }}</span></button>
      <button class="t" :class="{ active: tab === 'settings' }" @click="tab = 'settings'"><Icon name="settings" /><span>{{ t('nav.settings') }}</span></button>
    </nav>

    <ConfigDialog v-model:open="dialog.open" :config="dialog.config" :kind="dialog.kind" />
    <SettingsDialog />
  </div>
</template>

<style scoped>
.m-shell { height: 100%; width: 100%; max-width: 100vw; display: flex; flex-direction: column; background: var(--bg); overflow: hidden; }
.page { flex: 1; min-height: 0; min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.page.chat { min-width: 0; }
.m-head { display: flex; align-items: center; gap: 8px; height: 52px; padding: 0 12px; padding-top: env(safe-area-inset-top); background: var(--panel); border-bottom: 1px solid var(--border); flex-shrink: 0; }
.m-head.plain { padding: 0 16px; }
.m-head .big { font-size: 20px; font-weight: 600; }
.m-head .icon-btn { width: 36px; height: 36px; }
.m-head .icon-btn .icon { width: 22px; height: 22px; }
.ttl { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.ttl .r { display: flex; align-items: center; gap: 8px; }
.ttl .name { font-size: 16px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ttl .sub { font-size: 11.5px; }
.spacer { flex: 1; }
.search { display: flex; align-items: center; gap: 8px; margin: 10px 14px; height: 38px; padding: 0 12px; border-radius: 10px; background: var(--surface); color: var(--muted); flex-shrink: 0; }
.search input { flex: 1; border: none; background: transparent; outline: none; color: var(--text); font-family: inherit; font-size: 15px; }
.list { flex: 1; overflow-y: auto; padding-bottom: 8px; }
.ghead { padding: 12px 16px 6px; }
.row { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 60px; padding: 0 16px; border: none; background: var(--panel); border-bottom: 1px solid var(--border); cursor: pointer; font-family: inherit; color: var(--text); text-align: left; }
.row .text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.row .r { display: flex; align-items: center; gap: 8px; }
.row .name { font-size: 15px; font-weight: 500; }
.row .sub { font-size: 12.5px; }
.badge { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: var(--accent); color: #fff; font-size: 11px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; }
.peerbar { display: flex; gap: 8px; padding: 10px 14px; overflow-x: auto; background: var(--panel); border-bottom: 1px solid var(--border); flex-shrink: 0; }
.mchip { display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 12px; border-radius: 15px; background: var(--surface); color: var(--muted); font-size: 13px; font-weight: 500; white-space: nowrap; border: none; font-family: inherit; }
.mchip.active { background: var(--accent-soft); color: var(--accent-strong); }
.mchip.off { opacity: .55; }
.mchip .mini { min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: var(--accent); color: #fff; font-size: 10px; display: inline-flex; align-items: center; justify-content: center; }
.statusrow { display: flex; gap: 10px; padding: 8px 14px calc(10px + env(safe-area-inset-bottom)); background: var(--panel); border-top: 1px solid var(--border); flex-shrink: 0; }
.statusrow .btn { flex: 1; height: 44px; border-radius: 10px; border: 1px solid var(--border); background: var(--surface); color: var(--text); font-weight: 600; font-size: 15px; cursor: pointer; font-family: inherit; }
.statusrow .btn.primary { background: var(--accent); border-color: var(--accent); color: #fff; }
.statusrow .btn.danger { color: var(--red); }
.statusrow .btn.ghost { flex: 0 0 96px; }
.statusrow { box-sizing: border-box; }
.statusrow .btn { min-width: 0; box-sizing: border-box; }
.tabbar { height: calc(60px + env(safe-area-inset-bottom)); padding-bottom: env(safe-area-inset-bottom); display: flex; align-items: center; justify-content: space-around; background: var(--panel); border-top: 1px solid var(--border); flex-shrink: 0; }
.tabbar .t { display: flex; flex-direction: column; align-items: center; gap: 3px; font-size: 11px; color: var(--muted); width: 68px; border: none; background: transparent; cursor: pointer; font-family: inherit; }
.tabbar .t .icon { width: 22px; height: 22px; }
.tabbar .t.active { color: var(--accent); }
.backdrop { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: flex-end; z-index: 50; }
.sheet { width: 100%; max-height: 78%; background: var(--panel); border-radius: 18px 18px 0 0; display: flex; flex-direction: column; overflow: hidden; padding-bottom: env(safe-area-inset-bottom); }
.grip { width: 36px; height: 4px; border-radius: 2px; background: var(--faint); margin: 10px auto 4px; flex-shrink: 0; }
.sheet-body { overflow-y: auto; }
.sheet-body :deep(.panel) { width: 100%; border-left: none; }
.settings-page .setlist { padding: 8px 0; overflow-y: auto; }
.setrow { display: flex; align-items: center; gap: 12px; width: 100%; height: 52px; padding: 0 16px; border: none; background: var(--panel); border-bottom: 1px solid var(--border); font-size: 15px; color: var(--text); font-family: inherit; cursor: pointer; }
.setrow.static { cursor: default; color: var(--muted); }
.diag-inline { min-height: 320px; }
.diag-inline :deep(.diag) { grid-template-columns: 1fr; padding: 16px; }
.empty { padding: 40px 16px; text-align: center; color: var(--faint); font-size: 13px; }
</style>
