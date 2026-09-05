<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { NModal, NInput, NInputNumber, NSelect, NSwitch, NButton, NCollapse, NCollapseItem, NDynamicInput, useMessage } from 'naive-ui';
import Icon from './Icon.vue';
import { useConfigs } from '../stores/configs';
import { useSessions } from '../stores/sessions';
import { api } from '../api/commands';
import { defaultConfig, ENCODINGS, KIND_GROUPS, isServer, type Kind, type ReplyRule, type Resolved, type SessionConfig } from '../types';
import { isHex } from '../utils';

const props = defineProps<{ open: boolean; config: SessionConfig | null; kind: Kind }>();
const emit = defineEmits<{ 'update:open': [v: boolean] }>();
const configs = useConfigs();
const sessions = useSessions();
const message = useMessage();
const { t } = useI18n();

const form = ref<SessionConfig>(defaultConfig('tcpClient'));
const editing = computed(() => !!props.config);
const server = computed(() => isServer(form.value.kind));
const client = computed(() => form.value.kind === 'tcpClient' || form.value.kind === 'wsClient');
const isWs = computed(() => form.value.kind.startsWith('ws'));
const isUdp = computed(() => form.value.kind === 'udp');

watch(() => props.open, (o) => {
  if (!o) return;
  form.value = props.config ? JSON.parse(JSON.stringify(props.config)) : defaultConfig(props.kind);
  resolved.value = [];
  chosenIp.value = '';
});

function switchKind(k: Kind) {
  if (editing.value) return;
  const keep = { name: form.value.name };
  form.value = { ...defaultConfig(k), ...keep };
}

// inline DNS resolution
const resolved = ref<Resolved[]>([]);
const resolving = ref(false);
const resolveError = ref('');
const chosenIp = ref('');
let rt: number | undefined;
watch(() => form.value.host, (h) => {
  window.clearTimeout(rt);
  resolved.value = []; resolveError.value = ''; chosenIp.value = '';
  const host = h.trim().replace(/^wss?:\/\//, '').split('/')[0].split(':')[0];
  if (!host || /^[\d.]+$/.test(host) || host.includes(':') || server.value) return;
  rt = window.setTimeout(async () => {
    resolving.value = true;
    try { resolved.value = await api.resolveHost(host); } catch (e) { resolveError.value = String(e); } finally { resolving.value = false; }
  }, 400);
});

const encOpts = ENCODINGS.map((e) => ({ label: e.toUpperCase(), value: e }));
// NDynamicInput's `pair` preset works with { key, value } objects, but the backend
// SessionConfig.ws_headers is [string, string] tuples. Bridge the two here.
const wsHeaderPairs = computed<{ key: string; value: string }[]>({
  get: () => form.value.wsHeaders.map(([key, value]) => ({ key, value })),
  set: (pairs) => { form.value.wsHeaders = pairs.map((p) => [p?.key ?? '', p?.value ?? ''] as [string, string]); },
});
const fmtOpts = computed(() => [{ label: t('format.text'), value: 'text' }, { label: t('format.hex'), value: 'hex' }, { label: t('format.base64'), value: 'base64' }, { label: t('format.json'), value: 'json' }]);
const framingOpts = computed(() => [{ label: t('framing.noneHint'), value: 'none' }, { label: t('framing.delimiter'), value: 'delimiter' }, { label: t('framing.lengthPrefix'), value: 'lengthPrefix' }, { label: t('framing.fixed'), value: 'fixed' }, { label: t('framing.timeout'), value: 'timeout' }]);
const matchOpts = computed(() => [{ label: t('matchKind.exact'), value: 'exact' }, { label: t('matchKind.prefix'), value: 'prefix' }, { label: t('matchKind.contains'), value: 'contains' }, { label: t('matchKind.regex'), value: 'regex' }, { label: t('matchKind.hexPrefix'), value: 'hexPrefix' }]);
const actionOpts = computed(() => [{ label: t('replyAction.reply'), value: 'reply' }, { label: t('replyAction.echo'), value: 'echo' }, { label: t('replyAction.disconnect'), value: 'disconnect' }]);
const newRule = (): ReplyRule => ({ enabled: true, matchKind: 'prefix', pattern: '', action: 'reply', format: 'text', reply: '' });

function validate(): string | null {
  const f = form.value;
  if (!f.name.trim()) return t('validate.nameRequired');
  if (!isUdp.value && !f.host.trim()) return t('validate.hostRequired');
  if (!server.value && (f.port < 1 || f.port > 65535)) return t('validate.portRange');
  if (f.framing.mode === 'delimiter' && !isHex(f.framing.delimiterHex)) return t('validate.delimiterHex');
  if (f.timedSend.enabled) {
    if (!f.timedSend.content.trim()) return t('validate.timedContent');
    if (f.timedSend.format === 'hex' && !isHex(f.timedSend.content)) return t('validate.timedHex');
  }
  for (const r of f.autoReply.rules) {
    if (r.matchKind === 'hexPrefix' && !isHex(r.pattern)) return t('validate.hexPrefix');
    if (r.action === 'reply' && r.format === 'hex' && !isHex(r.reply)) return t('validate.replyHex');
  }
  return null;
}

const saving = ref(false);
async function save(andStart: boolean) {
  const err = validate();
  if (err) { message.warning(err); return; }
  saving.value = true;
  try {
    const f = { ...form.value };
    if (chosenIp.value && !server.value) f.host = chosenIp.value;
    f.port = Number(f.port);
    const wasOnline = editing.value && sessions.rt(f.uid).online;
    const saved = await configs.save(f);
    emit('update:open', false);
    sessions.select(saved.uid);
    if (wasOnline) {
      const old = props.config!;
      const changed = old.host !== saved.host || old.port !== saved.port || old.localPort !== saved.localPort || JSON.stringify(old.framing) !== JSON.stringify(saved.framing);
      if (changed) { await api.stopSession(saved.uid); await api.startSession(saved.uid); message.info(t('dialog.restarted')); }
      else message.info(t('dialog.savedNextTime'));
    } else if (andStart) {
      await api.startSession(saved.uid);
    }
  } catch (e) {
    message.error(String(e));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <NModal :show="open" preset="card" :title="editing ? t('dialog.editSession') : t('dialog.newSession')" style="width: 640px" :mask-closable="false" @update:show="emit('update:open', $event)">
    <div class="form">
      <div class="label">{{ t('dialog.type') }}</div>
      <div class="types">
        <button v-for="g in KIND_GROUPS" :key="g.kind" class="type" :class="{ active: form.kind === g.kind, disabled: editing && form.kind !== g.kind }" :disabled="editing" @click="switchKind(g.kind)">
          <span class="t">{{ t(`kind.${g.kind}`) }}</span>
          <span class="s">{{ t(`kindDesc.${g.kind}`) }}</span>
        </button>
      </div>

      <div class="field"><span class="label">{{ t('dialog.name') }}</span><NInput v-model:value="form.name" :placeholder="t('dialog.namePlaceholder')" /></div>

      <div class="two" :style="{ gridTemplateColumns: isUdp ? '1fr 140px' : '1fr 140px' }">
        <div class="field">
          <span class="label">{{ server ? t('dialog.listenAddr') : isUdp ? t('dialog.hostUdp') : t('dialog.host') }}</span>
          <NInput v-model:value="form.host" class="mono" :placeholder="server ? t('dialog.hostPlaceholderServer') : isWs ? t('dialog.hostPlaceholderWs') : t('dialog.hostPlaceholder')" />
        </div>
        <div class="field"><span class="label">{{ isUdp ? t('dialog.portUdp') : t('dialog.port') }}</span><NInputNumber v-model:value="form.port" :min="0" :max="65535" :show-button="false" class="mono" /></div>
      </div>
      <div v-if="resolving" class="hint">{{ t('dialog.resolving') }}</div>
      <div v-else-if="resolveError" class="hint" style="color:var(--red)">{{ resolveError }}</div>
      <div v-else-if="resolved.length" class="resolved">
        <div class="hint" style="color:var(--green);display:flex;align-items:center;gap:4px"><Icon name="check" size="sm" />{{ t('dialog.resolvedN', { n: resolved.length }) }}</div>
        <div class="ips">
          <button v-for="r in resolved" :key="r.ip" class="ip" :class="{ active: chosenIp === r.ip }" @click="chosenIp = chosenIp === r.ip ? '' : r.ip">
            <span class="radio"><span v-if="chosenIp === r.ip"></span></span><span class="mono">{{ r.ip }}</span><span class="muted">{{ r.recordType }} · {{ r.elapsedMs }} ms</span>
          </button>
        </div>
      </div>

      <div v-if="isUdp" class="two" style="grid-template-columns: 140px 1fr 1fr">
        <div class="field"><span class="label">{{ t('dialog.localPortRandom') }}</span><NInputNumber v-model:value="form.localPort" :min="0" :max="65535" :show-button="false" class="mono" /></div>
        <div class="field"><span class="label">{{ t('info.broadcast') }}</span><div style="height:32px;display:flex;align-items:center"><NSwitch v-model:value="form.broadcast" /></div></div>
        <div class="field"><span class="label">{{ t('dialog.mcastGroupOpt') }}</span><NInput v-model:value="form.multicastGroup" class="mono" placeholder="239.0.0.1" /></div>
      </div>
      <div v-if="isWs" class="field"><span class="label">{{ t('info.path') }}</span><NInput v-model:value="form.wsPath" class="mono" placeholder="/ws" /></div>

      <NCollapse arrow-placement="left" style="margin-top:4px">
        <NCollapseItem v-if="client" :title="t('dialog.connParams')" name="conn">
          <div class="two" style="grid-template-columns: 1fr 1fr 1fr">
            <div class="field"><span class="label">{{ t('dialog.connectTimeoutMs') }}</span><NInputNumber v-model:value="form.connectTimeoutMs" :min="100" :show-button="false" /></div>
            <div class="field"><span class="label">{{ t('dialog.localBindHint') }}</span><NInput v-model:value="form.localBind" class="mono" :placeholder="t('common.auto')" /></div>
            <div class="field"><span class="label">{{ t('info.autoReconnect') }}</span><div style="height:32px;display:flex;align-items:center"><NSwitch v-model:value="form.autoReconnect" /></div></div>
          </div>
          <div v-if="form.autoReconnect" class="two" style="grid-template-columns: 1fr 1fr">
            <div class="field"><span class="label">{{ t('dialog.reconnectInterval') }}</span><NInputNumber v-model:value="form.reconnectIntervalMs" :min="200" :show-button="false" /></div>
            <div class="field"><span class="label">{{ t('dialog.reconnectMax') }}</span><NInputNumber v-model:value="form.reconnectMax" :min="0" :show-button="false" /></div>
          </div>
          <div v-if="form.kind === 'tcpClient'" class="two" style="grid-template-columns: 1fr 1fr">
            <div class="field"><span class="label">TCP_NODELAY</span><NSwitch v-model:value="form.nodelay" /></div>
            <div class="field"><span class="label">SO_KEEPALIVE</span><NSwitch v-model:value="form.keepalive" /></div>
          </div>
          <div v-if="form.kind === 'wsClient'" class="field"><span class="label">{{ t('info.headers') }}</span>
            <NDynamicInput v-model:value="wsHeaderPairs" preset="pair" key-placeholder="Header" :value-placeholder="t('info.headers')" />
          </div>
        </NCollapseItem>
        <NCollapseItem v-if="server" :title="t('dialog.srvParams')" name="srv">
          <div class="field"><span class="label">{{ t('dialog.maxConnHint') }}</span><NInputNumber v-model:value="form.maxConnections" :min="0" :show-button="false" /></div>
        </NCollapseItem>
        <NCollapseItem v-if="!isUdp" :title="t('dialog.framingEnc')" name="framing">
          <div class="field"><span class="label">{{ t('dialog.tcpFraming') }}</span><NSelect v-model:value="form.framing.mode" :options="framingOpts" /></div>
          <div v-if="form.framing.mode === 'delimiter'" class="field"><span class="label">{{ t('dialog.delimiterHex') }}</span><NInput v-model:value="form.framing.delimiterHex" class="mono" placeholder="0D 0A" /></div>
          <div v-if="form.framing.mode === 'lengthPrefix'" class="two" style="grid-template-columns: 1fr 1fr 1fr 1fr">
            <div class="field"><span class="label">{{ t('dialog.lenOffset') }}</span><NInputNumber v-model:value="form.framing.lenOffset" :min="0" :show-button="false" /></div>
            <div class="field"><span class="label">{{ t('dialog.lenSize') }}</span><NSelect v-model:value="form.framing.lenSize" :options="[1, 2, 4].map((n) => ({ label: String(n), value: n }))" /></div>
            <div class="field"><span class="label">{{ t('dialog.bigEndian') }}</span><div style="height:32px;display:flex;align-items:center"><NSwitch v-model:value="form.framing.bigEndian" /></div></div>
            <div class="field"><span class="label">{{ t('dialog.lenIncludesHeader') }}</span><div style="height:32px;display:flex;align-items:center"><NSwitch v-model:value="form.framing.lenIncludesHeader" /></div></div>
          </div>
          <div v-if="form.framing.mode === 'fixed'" class="field"><span class="label">{{ t('dialog.frameLenBytes') }}</span><NInputNumber v-model:value="form.framing.fixedLen" :min="1" :show-button="false" /></div>
          <div v-if="form.framing.mode === 'timeout'" class="field"><span class="label">{{ t('dialog.aggWindowMs') }}</span><NInputNumber v-model:value="form.framing.timeoutMs" :min="1" :show-button="false" /></div>
          <div class="two" style="grid-template-columns: 1fr 1fr">
            <div class="field"><span class="label">{{ t('send.sendEncoding') }}</span><NSelect v-model:value="form.sendEncoding" :options="encOpts" /></div>
            <div class="field"><span class="label">{{ t('dialog.recvEncoding') }}</span><NSelect v-model:value="form.recvEncoding" :options="encOpts" /></div>
          </div>
        </NCollapseItem>
        <NCollapseItem v-else :title="t('dialog.encoding')" name="enc">
          <div class="two" style="grid-template-columns: 1fr 1fr">
            <div class="field"><span class="label">{{ t('send.sendEncoding') }}</span><NSelect v-model:value="form.sendEncoding" :options="encOpts" /></div>
            <div class="field"><span class="label">{{ t('dialog.recvEncoding') }}</span><NSelect v-model:value="form.recvEncoding" :options="encOpts" /></div>
          </div>
        </NCollapseItem>
        <NCollapseItem :title="t('info.autoReply')" name="reply">
          <div class="two" style="grid-template-columns: auto 1fr; align-items:center">
            <div class="field" style="flex-direction:row;align-items:center;gap:8px"><NSwitch v-model:value="form.autoReply.enabled" /><span class="label">{{ t('dialog.enableAutoReply') }}</span></div>
            <div class="field" style="flex-direction:row;align-items:center;gap:8px;justify-content:flex-end"><span class="label">{{ t('dialog.delayMs') }}</span><NInputNumber v-model:value="form.autoReply.delayMs" :min="0" :show-button="false" style="width:100px" /></div>
          </div>
          <div v-for="(r, i) in form.autoReply.rules" :key="i" class="rule">
            <NSwitch v-model:value="r.enabled" size="small" />
            <NSelect v-model:value="r.matchKind" :options="matchOpts" style="width:110px" />
            <NInput v-model:value="r.pattern" class="mono" :placeholder="t('dialog.matchPlaceholder')" />
            <NSelect v-model:value="r.action" :options="actionOpts" style="width:110px" />
            <NSelect v-if="r.action === 'reply'" v-model:value="r.format" :options="fmtOpts" style="width:90px" />
            <NInput v-if="r.action === 'reply'" v-model:value="r.reply" class="mono" :placeholder="t('dialog.replyPlaceholder')" />
            <button class="icon-btn" @click="form.autoReply.rules.splice(i, 1)"><Icon name="x" size="sm" /></button>
          </div>
          <button class="chip" style="margin-top:6px" @click="form.autoReply.rules.push(newRule())"><Icon name="plus" size="sm" />{{ t('dialog.addRule') }}</button>
          <div class="two" style="grid-template-columns: auto 90px 1fr; margin-top:10px; align-items:center">
            <div class="field" style="flex-direction:row;align-items:center;gap:8px"><NSwitch v-model:value="form.autoReply.defaultEnabled" size="small" /><span class="label">{{ t('dialog.defaultWhenNoMatch') }}</span></div>
            <NSelect v-model:value="form.autoReply.defaultFormat" :options="fmtOpts" :disabled="!form.autoReply.defaultEnabled" />
            <NInput v-model:value="form.autoReply.defaultReply" class="mono" :disabled="!form.autoReply.defaultEnabled" :placeholder="t('dialog.defaultReplyPlaceholder')" />
          </div>
        </NCollapseItem>
        <NCollapseItem v-if="!server" :title="t('info.timedSend')" name="timed">
          <div class="two" style="grid-template-columns: auto 1fr 1fr; align-items:center">
            <div class="field" style="flex-direction:row;align-items:center;gap:8px"><NSwitch v-model:value="form.timedSend.enabled" /><span class="label">{{ t('dialog.enableTimed') }}</span></div>
            <div class="field"><span class="label">{{ t('dialog.intervalMs') }}</span><NInputNumber v-model:value="form.timedSend.intervalMs" :min="1" :show-button="false" /></div>
            <div class="field"><span class="label">{{ t('dialog.countTimes') }}</span><NInputNumber v-model:value="form.timedSend.count" :min="0" :show-button="false" /></div>
          </div>
          <div class="two" style="grid-template-columns: 110px 1fr">
            <div class="field"><span class="label">{{ t('snippets.format') }}</span><NSelect v-model:value="form.timedSend.format" :options="fmtOpts" /></div>
            <div class="field"><span class="label">{{ t('snippets.content') }}</span><NInput v-model:value="form.timedSend.content" class="mono" :placeholder="t('dialog.contentPlaceholder')" /></div>
          </div>
        </NCollapseItem>
      </NCollapse>
    </div>
    <template #footer>
      <div style="display:flex;justify-content:flex-end;gap:8px">
        <NButton quaternary @click="emit('update:open', false)">{{ t('common.cancel') }}</NButton>
        <NButton :loading="saving" @click="save(false)">{{ editing ? t('common.save') : t('common.saveOnly') }}</NButton>
        <NButton v-if="!editing" type="primary" :loading="saving" @click="save(true)"><template #icon><Icon name="zap" /></template>{{ server ? t('dialog.createListen') : t('dialog.createConnect') }}</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 12px; max-height: 70vh; overflow-y: auto; padding-right: 4px; }
.types { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.type { display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--border); background: var(--panel); text-align: left; cursor: pointer; font-family: inherit; color: var(--text); }
.type.active { border-color: var(--accent); background: var(--accent-soft); }
.type.active .t { color: var(--accent-strong); }
.type.disabled { opacity: .4; cursor: default; }
.type .t { font-weight: 600; font-size: 12.5px; }
.type .s { font-size: 11px; color: var(--muted); }
.field { display: flex; flex-direction: column; gap: 6px; }
.two { display: grid; gap: 12px; }
.hint { font-size: 11.5px; color: var(--muted); margin-top: -4px; }
.resolved { display: flex; flex-direction: column; gap: 6px; margin-top: -4px; }
.ips { display: flex; flex-direction: column; gap: 2px; padding: 4px; border-radius: 8px; background: var(--surface); }
.ip { display: flex; align-items: center; gap: 10px; height: 30px; padding: 0 10px; border-radius: 6px; border: none; background: transparent; cursor: pointer; font-family: inherit; color: var(--text); font-size: 12.5px; }
.ip.active { background: var(--accent-soft); }
.ip .radio { width: 14px; height: 14px; border-radius: 50%; border: 1.5px solid var(--faint); display: flex; align-items: center; justify-content: center; }
.ip.active .radio { border-color: var(--accent); }
.ip .radio span { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); }
.ip .muted { margin-left: auto; font-size: 11px; }
.rule { display: flex; align-items: center; gap: 6px; margin-top: 8px; }
</style>
