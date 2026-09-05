<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMessage } from 'naive-ui';
import Icon from './Icon.vue';
import { useSessions } from '../stores/sessions';
import { api } from '../api/commands';
import { isServer, type SessionConfig } from '../types';
import { fmtBytes, fmtTime } from '../utils';

const props = defineProps<{ config: SessionConfig }>();
const sessions = useSessions();
const message = useMessage();
const { t } = useI18n();
const rt = computed(() => sessions.rt(props.config.uid));
const open = ref<Record<string, boolean>>({ peers: true, stats: true, framing: true, reply: true, conn: false });
const showPeers = computed(() => isServer(props.config.kind) || props.config.kind === 'udp');
const onlinePeers = computed(() => rt.value.peers.filter((p) => p.online).length);

async function kick(addr: string) {
  try { await api.kickPeer(props.config.uid, addr); } catch (e) { message.error(String(e)); }
}
const FRAMING: Record<string, string> = { none: 'framing.none', delimiter: 'framing.delimiter', lengthPrefix: 'framing.lengthPrefix', fixed: 'framing.fixed', timeout: 'framing.timeout' };
const MATCH: Record<string, string> = { exact: 'matchKind.exact', prefix: 'matchKind.prefix', contains: 'matchKind.contains', regex: 'matchKind.regex', hexPrefix: 'matchKind.hexPrefix' };
const ACTION: Record<string, string> = { reply: 'replyAction.replyShort', echo: 'replyAction.echoShort', disconnect: 'replyAction.disconnectShort' };
</script>

<template>
  <aside class="panel">
    <section v-if="showPeers">
      <button class="head" @click="open.peers = !open.peers"><span class="section-title">{{ t('info.peersOnline', { n: onlinePeers }) }}</span><Icon :name="open.peers ? 'chevron' : 'chevronR'" size="sm" /></button>
      <div v-if="open.peers" class="body">
        <div v-if="!rt.peers.length" class="faint" style="font-size:12px">{{ t('info.noPeers') }}</div>
        <div v-for="p in rt.peers" :key="p.addr" class="peer" :class="{ active: sessions.activePeer === p.addr }" @click="sessions.selectPeer(sessions.activePeer === p.addr ? null : p.addr)">
          <span class="dot" :class="{ on: p.online }" style="box-shadow:none;width:7px;height:7px"></span>
          <div class="pt">
            <span class="mono" :class="{ muted: !p.online }" style="font-size:12px">{{ p.addr }}</span>
            <span class="faint" style="font-size:10.5px">{{ p.online ? t('info.joined') : t('info.left') }} {{ fmtTime(p.since, false) }}</span>
          </div>
          <span v-if="rt.unreadByPeer[p.addr]" class="badge">{{ rt.unreadByPeer[p.addr] }}</span>
          <button v-if="p.online && isServer(config.kind)" class="icon-btn kick" :title="t('info.kick')" @click.stop="kick(p.addr)"><Icon name="kick" size="sm" /></button>
        </div>
        <div v-if="sessions.activePeer" class="faint" style="font-size:11px">{{ t('info.filtered', { peer: sessions.activePeer }) }}</div>
      </div>
    </section>

    <section>
      <button class="head" @click="open.stats = !open.stats"><span class="section-title">{{ t('info.stats') }}</span><Icon :name="open.stats ? 'chevron' : 'chevronR'" size="sm" /></button>
      <div v-if="open.stats" class="body grid">
        <div class="stat"><span class="label">{{ t('info.received') }}</span><span class="mono v">{{ fmtBytes(rt.bytesIn) }}</span><span class="faint">{{ t('info.count', { n: rt.countIn }) }}</span></div>
        <div class="stat"><span class="label">{{ t('info.sent') }}</span><span class="mono v">{{ fmtBytes(rt.bytesOut) }}</span><span class="faint">{{ t('info.count', { n: rt.countOut }) }}</span></div>
        <div class="stat" v-if="showPeers"><span class="label">{{ t('info.totalPeers') }}</span><span class="mono v">{{ rt.totalPeers }}</span></div>
        <div class="stat"><span class="label">{{ t('info.errors') }}</span><span class="mono v" :style="{ color: rt.errors ? 'var(--red)' : undefined }">{{ rt.errors }}</span></div>
      </div>
    </section>

    <section v-if="config.kind !== 'udp'">
      <button class="head" @click="open.framing = !open.framing"><span class="section-title">{{ t('info.framingEnc') }}</span><Icon :name="open.framing ? 'chevron' : 'chevronR'" size="sm" /></button>
      <div v-if="open.framing" class="body">
        <div class="kv"><span class="k">{{ t('info.rule') }}</span><span class="v">{{ t(FRAMING[config.framing.mode]) }}</span></div>
        <div v-if="config.framing.mode === 'delimiter'" class="kv"><span class="k">{{ t('info.delimiter') }}</span><span class="v mono">{{ config.framing.delimiterHex }}</span></div>
        <div v-if="config.framing.mode === 'lengthPrefix'" class="kv"><span class="k">{{ t('info.lenField') }}</span><span class="v mono">{{ t('info.offsetByteEndian', { offset: config.framing.lenOffset, size: config.framing.lenSize, endian: config.framing.bigEndian ? t('info.bigEndian') : t('info.littleEndian') }) }}</span></div>
        <div v-if="config.framing.mode === 'fixed'" class="kv"><span class="k">{{ t('info.frameLen') }}</span><span class="v mono">{{ t('info.bytesUnit', { n: config.framing.fixedLen }) }}</span></div>
        <div v-if="config.framing.mode === 'timeout'" class="kv"><span class="k">{{ t('info.aggWindow') }}</span><span class="v mono">{{ t('info.msUnit', { n: config.framing.timeoutMs }) }}</span></div>
        <div class="kv"><span class="k">{{ t('info.recvEncoding') }}</span><span class="v">{{ config.recvEncoding.toUpperCase() }}</span></div>
      </div>
    </section>

    <section>
      <button class="head" @click="open.reply = !open.reply"><span class="section-title">{{ t('info.autoReply') }} · {{ config.autoReply.enabled ? t('info.rulesN', { n: config.autoReply.rules.filter((r) => r.enabled).length }) : t('common.off') }}</span><Icon :name="open.reply ? 'chevron' : 'chevronR'" size="sm" /></button>
      <div v-if="open.reply && config.autoReply.enabled" class="body">
        <div v-for="(r, i) in config.autoReply.rules.filter((r) => r.enabled)" :key="i" class="kv"><span class="mono" style="font-size:12px;overflow:hidden;text-overflow:ellipsis">{{ t(MATCH[r.matchKind]) }} {{ r.pattern }}</span><span class="muted">→ {{ t(ACTION[r.action]) }}</span></div>
        <div v-if="config.autoReply.defaultEnabled" class="kv"><span class="muted">{{ t('common.other') }}</span><span class="muted">→ {{ t('info.defaultReply') }}</span></div>
        <div class="kv"><span class="k">{{ t('info.delay') }}</span><span class="v">{{ config.autoReply.delayMs }} ms</span></div>
      </div>
    </section>

    <section>
      <button class="head" @click="open.conn = !open.conn"><span class="section-title">{{ t('info.connParams') }}</span><Icon :name="open.conn ? 'chevron' : 'chevronR'" size="sm" /></button>
      <div v-if="open.conn" class="body">
        <template v-if="config.kind === 'tcpClient' || config.kind === 'wsClient'">
          <div class="kv"><span class="k">{{ t('info.connectTimeout') }}</span><span class="v">{{ config.connectTimeoutMs }} ms</span></div>
          <div class="kv"><span class="k">{{ t('info.autoReconnect') }}</span><span class="v">{{ config.autoReconnect ? t('info.reconnectDetail', { interval: config.reconnectIntervalMs, max: config.reconnectMax || t('common.unlimited') }) : t('common.off') }}</span></div>
          <div class="kv"><span class="k">{{ t('info.localBind') }}</span><span class="v mono">{{ config.localBind || t('common.auto') }}</span></div>
          <div class="kv"><span class="k">{{ t('info.nodelayKeepalive') }}</span><span class="v">{{ config.nodelay ? t('common.on') : t('common.off') }} / {{ config.keepalive ? t('common.on') : t('common.off') }}</span></div>
        </template>
        <template v-if="isServer(config.kind)">
          <div class="kv"><span class="k">{{ t('info.maxConn') }}</span><span class="v">{{ config.maxConnections || t('common.unlimited') }}</span></div>
        </template>
        <template v-if="config.kind === 'udp'">
          <div class="kv"><span class="k">{{ t('info.localPort') }}</span><span class="v mono">{{ config.localPort || t('common.auto') }}</span></div>
          <div class="kv"><span class="k">{{ t('info.broadcast') }}</span><span class="v">{{ config.broadcast ? t('common.on') : t('common.off') }}</span></div>
          <div class="kv"><span class="k">{{ t('info.mcastGroup') }}</span><span class="v mono">{{ config.multicastGroup || t('common.none') }}</span></div>
        </template>
        <template v-if="config.kind.startsWith('ws')">
          <div class="kv"><span class="k">{{ t('info.path') }}</span><span class="v mono">{{ config.wsPath }}</span></div>
          <div class="kv" v-if="config.wsHeaders.length"><span class="k">{{ t('info.headers') }}</span><span class="v">{{ t('info.headersN', { n: config.wsHeaders.length }) }}</span></div>
        </template>
        <div class="kv" v-if="config.timedSend.enabled"><span class="k">{{ t('info.timedSend') }}</span><span class="v">{{ t('info.everyTimes', { interval: config.timedSend.intervalMs, count: config.timedSend.count || t('common.unlimited') }) }}</span></div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.panel { width: 290px; flex-shrink: 0; border-left: 1px solid var(--border); background: var(--panel); overflow-y: auto; }
section { border-bottom: 1px solid var(--border); padding: 10px 14px 12px; }
.head { display: flex; align-items: center; justify-content: space-between; width: 100%; border: none; background: transparent; padding: 2px 0; cursor: pointer; color: var(--muted); font-family: inherit; }
.body { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.stat { display: flex; flex-direction: column; gap: 1px; }
.stat .v { font-size: 15px; font-weight: 500; }
.stat .faint { font-size: 11px; }
.peer { display: flex; align-items: center; gap: 8px; height: 36px; padding: 0 6px; margin: 0 -6px; border-radius: 6px; cursor: pointer; }
.peer:hover, .peer.active { background: var(--surface); }
.peer .pt { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.peer .kick { opacity: 0; width: 24px; height: 24px; }
.peer:hover .kick { opacity: 1; }
.badge { min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: var(--accent); color: #fff; font-size: 10.5px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; }
</style>
