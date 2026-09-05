<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMessage } from 'naive-ui';
import Icon from './Icon.vue';
import { useSessions } from '../stores/sessions';
import { useUi } from '../stores/ui';
import { api } from '../api/commands';
import { KIND_LABEL, isServer, type SessionConfig } from '../types';
import { fmtDuration } from '../utils';

const props = defineProps<{ config: SessionConfig }>();
const emit = defineEmits<{ edit: [] }>();
const sessions = useSessions();
const ui = useUi();
const message = useMessage();
const { t } = useI18n();
const rt = computed(() => sessions.rt(props.config.uid));
const server = computed(() => isServer(props.config.kind) || props.config.kind === 'udp');

const now = ref(Date.now());
const timer = setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => clearInterval(timer));
const uptime = computed(() => (rt.value.onlineSince ? fmtDuration(now.value - rt.value.onlineSince) : ''));

const statusText = computed(() => {
  if (rt.value.online) return server.value ? t('status.listening') : t('status.connected');
  if (rt.value.reconnecting) return `${t('status.reconnecting')} · ${rt.value.reconnecting}`;
  return server.value ? t('status.notListening') : t('status.disconnected');
});
const busy = ref(false);
async function toggle() {
  busy.value = true;
  try {
    if (rt.value.online || rt.value.reconnecting) await api.stopSession(props.config.uid);
    else await api.startSession(props.config.uid);
  } catch (e) {
    message.error(String(e));
    sessions.sys(props.config.uid, String(e), 'error');
  } finally {
    busy.value = false;
  }
}
const addr = computed(() => {
  const c = props.config;
  if (c.kind === 'udp') return `${t('common.local')} ${rt.value.local ?? (c.localPort || t('common.auto'))}${c.host ? ` → ${c.host}:${c.port}` : ''}`;
  if (rt.value.online && rt.value.local && server.value) return rt.value.local;
  return `${c.host}:${c.port}${c.kind.startsWith('ws') && !c.host.startsWith('ws') ? c.wsPath : ''}`;
});
</script>

<template>
  <header class="head">
    <div class="title">
      <div class="row"><span class="name">{{ config.name }}</span><span class="chip" style="height:20px;font-size:11px;cursor:default">{{ t(KIND_LABEL[config.kind]) }}</span></div>
      <span class="mono muted addr">{{ addr }}</span>
    </div>
    <div class="spacer"></div>
    <div class="status">
      <span class="dot" :class="{ on: rt.online, warn: !!rt.reconnecting }"></span>
      <span :style="{ color: rt.online ? 'var(--green)' : 'var(--muted)', fontWeight: 500 }">{{ statusText }}</span>
      <span v-if="uptime" class="mono muted">{{ uptime }}</span>
    </div>
    <button class="btn" :class="rt.online || rt.reconnecting ? 'danger' : 'primary'" :disabled="busy" @click="toggle">
      {{ rt.online || rt.reconnecting ? (server ? t('status.closeListen') : t('status.disconnect')) : server ? t('status.startListen') : t('status.connect') }}
    </button>
    <button class="icon-btn" :title="t('common.edit')" @click="emit('edit')"><Icon name="edit" /></button>
    <button class="icon-btn" :title="t('common.clear')" @click="sessions.clear(config.uid)"><Icon name="trash" /></button>
    <button class="icon-btn" :title="t('nav.settings')" @click="ui.panelOpen = !ui.panelOpen"><Icon name="panel" /></button>
  </header>
</template>

<style scoped>
.head { height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 16px; border-bottom: 1px solid var(--border); background: var(--panel); flex-shrink: 0; }
.title { min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.row { display: flex; align-items: center; gap: 8px; }
.name { font-size: 15px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.addr { font-size: 11.5px; }
.spacer { flex: 1; }
.status { display: flex; align-items: center; gap: 6px; font-size: 12.5px; margin-right: 6px; }
.btn { height: 30px; padding: 0 14px; border-radius: 6px; border: 1px solid var(--border); background: var(--panel); color: var(--text); font-weight: 500; cursor: pointer; font-family: inherit; font-size: 13px; }
.btn.primary { background: var(--accent); border-color: var(--accent); color: #fff; }
.btn.danger { color: var(--red); }
.btn:disabled { opacity: .6; cursor: default; }
</style>
