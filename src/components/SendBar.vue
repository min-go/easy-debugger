<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { NDropdown, NPopover, useMessage } from 'naive-ui';
import Icon from './Icon.vue';
import { useSessions } from '../stores/sessions';
import { useSnippets } from '../stores/snippets';
import { api } from '../api/commands';
import { ENCODINGS, isServer, type Checksum, type Format, type LineEnding, type SendRequest, type SessionConfig } from '../types';

const props = defineProps<{ config: SessionConfig }>();
const sessions = useSessions();
const snippets = useSnippets();
const message = useMessage();
const { t } = useI18n();
const rt = computed(() => sessions.rt(props.config.uid));

const content = ref('');
const format = ref<Format>('text');
const encoding = ref(props.config.sendEncoding || 'utf-8');
const unescape = ref(false);
const template = ref(true);
const lineEnding = ref<LineEnding>('none');
const customEnding = ref('');
const checksum = ref<Checksum>('none');
const bigEndian = ref(false);
const broadcast = ref(false);
watch(() => props.config.uid, () => { encoding.value = props.config.sendEncoding || 'utf-8'; });

const FORMATS: { key: Format; label: string }[] = [{ key: 'text', label: 'format.text' }, { key: 'hex', label: 'format.hex' }, { key: 'base64', label: 'format.base64' }, { key: 'json', label: 'format.json' }];
const ENDINGS: { key: LineEnding; label: string }[] = [{ key: 'none', label: 'lineEnding.none' }, { key: 'lf', label: 'lineEnding.lf' }, { key: 'crLf', label: 'lineEnding.crLf' }, { key: 'cr', label: 'lineEnding.cr' }, { key: 'nul', label: 'lineEnding.nul' }, { key: 'custom', label: 'lineEnding.custom' }];
const CHECKSUMS: { key: Checksum; label: string }[] = [{ key: 'none', label: 'checksum.none' }, { key: 'crc16Modbus', label: 'checksum.crc16Modbus' }, { key: 'crc16Ccitt', label: 'checksum.crc16Ccitt' }, { key: 'crc32', label: 'checksum.crc32' }, { key: 'xor', label: 'checksum.xor' }, { key: 'sum8', label: 'checksum.sum8' }];
const VARS = ['{{ts}}', '{{ts_ms}}', '{{datetime}}', '{{seq}}', '{{rand:4}}', '{{rand_hex:8}}', '{{uuid}}'];
const label = (list: { key: string; label: string }[], k: string) => { const it = list.find((x) => x.key === k); return it ? t(it.label) : k; };

const request = computed<SendRequest>(() => ({
  format: format.value, content: content.value, encoding: encoding.value, unescape: unescape.value, template: template.value,
  lineEnding: lineEnding.value, customEndingHex: customEnding.value, checksum: checksum.value, checksumBigEndian: bigEndian.value,
}));

const preview = ref<{ hex: string; len: number; checksumOffset: number | null } | null>(null);
const previewError = ref('');
let previewTimer: number | undefined;
watch(request, () => {
  window.clearTimeout(previewTimer);
  previewTimer = window.setTimeout(async () => {
    if (!content.value) { preview.value = null; previewError.value = ''; return; }
    try { preview.value = await api.previewPayload(request.value); previewError.value = ''; } catch (e) { preview.value = null; previewError.value = String(e); }
  }, 120);
}, { deep: true });
const previewParts = computed(() => {
  if (!preview.value) return null;
  const parts = preview.value.hex.split(' ');
  const off = preview.value.checksumOffset;
  return { head: parts.slice(0, off ?? parts.length).join(' '), tail: off != null ? parts.slice(off).join(' ') : '' };
});

const history = ref<string[]>([]);
let histIdx = -1;
const target = computed(() => (isServer(props.config.kind) || props.config.kind === 'udp' ? (broadcast.value ? null : sessions.activePeer) : null));
const needsPeer = computed(() => (isServer(props.config.kind)) && !broadcast.value && !sessions.activePeer);
const sending = ref(false);

async function send() {
  if (!content.value || sending.value) return;
  if (!rt.value.online) { message.warning(t('send.notStarted')); return; }
  if (needsPeer.value) { message.warning(t('send.choosePeer')); return; }
  sending.value = true;
  try {
    await api.sendMessage(props.config.uid, target.value, request.value);
    if (history.value[0] !== content.value) history.value.unshift(content.value);
    if (history.value.length > 100) history.value.length = 100;
    histIdx = -1;
    content.value = '';
  } catch (e) {
    message.error(String(e));
  } finally {
    sending.value = false;
  }
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(); return; }
  if ((e.key === 'ArrowUp' || e.key === 'ArrowDown') && (content.value === '' || histIdx >= 0)) {
    e.preventDefault();
    histIdx = e.key === 'ArrowUp' ? Math.min(histIdx + 1, history.value.length - 1) : Math.max(histIdx - 1, -1);
    content.value = histIdx >= 0 ? history.value[histIdx] : '';
  }
}
function insertVar(v: string) { content.value += v; }
const snippetOptions = computed(() => snippets.list.map((s) => ({ label: `${s.name}${s.group ? ` · ${s.group}` : ''}`, key: s.uid })));
function useSnippet(key: string) {
  const s = snippets.list.find((x) => x.uid === key);
  if (s) { content.value = s.content; format.value = s.format; }
}
async function saveSnippet() {
  if (!content.value) return;
  const name = window.prompt(t('send.snippetName'), content.value.slice(0, 20));
  if (!name) return;
  await snippets.save({ uid: '', name, group: '', format: format.value, content: content.value });
  message.success(t('send.savedToLib'));
}
const showTimed = computed(() => props.config.timedSend.enabled && !isServer(props.config.kind));
</script>

<template>
  <div class="sendbar">
    <div class="tools">
      <button v-for="f in FORMATS" :key="f.key" class="chip" :class="{ active: format === f.key }" @click="format = f.key">{{ t(f.label) }}</button>
      <span class="sep"></span>
      <NPopover trigger="click" placement="top-start">
        <template #trigger><button class="chip" :class="{ active: encoding !== 'utf-8' || unescape }">{{ encoding.toUpperCase() }}<Icon name="chevron" size="sm" /></button></template>
        <div class="pop">
          <div class="label">{{ t('send.sendEncoding') }}</div>
          <div class="opts"><button v-for="e in ENCODINGS" :key="e" class="chip" :class="{ active: encoding === e }" @click="encoding = e">{{ e }}</button></div>
          <label class="check"><input type="checkbox" v-model="unescape" /> {{ t('send.encodingParse') }}</label>
          <label class="check"><input type="checkbox" v-model="template" /> {{ t('send.expandVars') }}</label>
        </div>
      </NPopover>
      <NPopover trigger="click" placement="top-start">
        <template #trigger><button class="chip" :class="{ active: lineEnding !== 'none' }">{{ t('lineEnding.label') }} <b>{{ label(ENDINGS, lineEnding) }}</b><Icon name="chevron" size="sm" /></button></template>
        <div class="pop">
          <div class="opts"><button v-for="e in ENDINGS" :key="e.key" class="chip" :class="{ active: lineEnding === e.key }" @click="lineEnding = e.key">{{ e.label }}</button></div>
          <input v-if="lineEnding === 'custom'" v-model="customEnding" class="inp mono" :placeholder="t('send.customHex')" />
        </div>
      </NPopover>
      <NPopover trigger="click" placement="top-start">
        <template #trigger><button class="chip" :class="{ active: checksum !== 'none' }">{{ t('checksum.label') }} <b>{{ label(CHECKSUMS, checksum) }}</b><Icon name="chevron" size="sm" /></button></template>
        <div class="pop">
          <div class="opts"><button v-for="c in CHECKSUMS" :key="c.key" class="chip" :class="{ active: checksum === c.key }" @click="checksum = c.key">{{ c.label }}</button></div>
          <label v-if="checksum !== 'none' && checksum !== 'xor' && checksum !== 'sum8'" class="check"><input type="checkbox" v-model="bigEndian" /> {{ t('dialog.bigEndian') }}</label>
        </div>
      </NPopover>
      <NDropdown :options="VARS.map((v) => ({ label: v, key: v }))" trigger="click" @select="insertVar">
        <button class="chip"><Icon name="braces" size="sm" />{{ t('send.vars') }}</button>
      </NDropdown>
      <NDropdown :options="snippetOptions.length ? snippetOptions : [{ label: t('send.snippetLibEmpty'), key: '', disabled: true }]" trigger="click" @select="useSnippet">
        <button class="chip"><Icon name="snippets" size="sm" />{{ t('send.snippets') }}</button>
      </NDropdown>
      <button class="chip" :title="t('send.saveSnippet')" @click="saveSnippet"><Icon name="plus" size="sm" /></button>
      <span class="spacer"></span>
      <label v-if="isServer(config.kind) || config.kind === 'udp'" class="chip" :class="{ active: broadcast }" style="cursor:pointer"><input type="checkbox" v-model="broadcast" style="margin:0 2px 0 0" />{{ config.kind === 'udp' ? t('send.sendToDefault') : t('send.broadcast') }}</label>
      <span v-if="showTimed" class="chip active"><Icon name="clock" size="sm" />{{ t('send.timed') }} {{ config.timedSend.intervalMs }} ms</span>
    </div>
    <div class="row">
      <textarea v-model="content" :class="{ mono: format !== 'text' }" :placeholder="format === 'hex' ? t('send.placeholderHex') : t('send.placeholder')" rows="2" @keydown="onKey" spellcheck="false"></textarea>
      <button class="send" :disabled="sending || !content" @click="send"><Icon name="send" /><span class="kbd" style="border-color:rgba(255,255,255,.35);color:#fff;background:transparent">↩</span></button>
    </div>
    <div class="preview muted">
      <template v-if="previewError"><span style="color:var(--red)">{{ previewError }}</span></template>
      <template v-else-if="previewParts">
        <span>{{ t('send.preview') }}</span>
        <span class="mono hex">{{ previewParts.head }}<span v-if="previewParts.tail" class="sum"> {{ previewParts.tail }}</span></span>
        <span>· {{ t('send.bytes', { n: preview!.len }) }}</span>
        <span v-if="target">· {{ t('send.sendTo', { addr: target }) }}</span>
        <span v-else-if="isServer(config.kind)">· {{ broadcast ? t('send.broadcast') : t('send.noPeerChosen') }}</span>
      </template>
      <template v-else><span>{{ t('send.history') }}</span></template>
    </div>
  </div>
</template>

<style scoped>
.sendbar { border-top: 1px solid var(--border); background: var(--panel); padding: 8px 16px 10px; display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; min-width: 0; }
.tools { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.sep { width: 1px; height: 16px; background: var(--border); margin: 0 4px; }
.spacer { flex: 1; }
.row { display: flex; gap: 10px; align-items: stretch; }
textarea { flex: 1; min-width: 0; min-height: 56px; max-height: 200px; padding: 8px 10px; border-radius: 6px; border: 1px solid var(--border); background: var(--bg); color: var(--text); font-family: var(--sans); font-size: 13px; line-height: 1.55; resize: vertical; outline: none; user-select: text; }
textarea:focus { border-color: var(--accent); }
textarea.mono { font-family: var(--mono); font-size: 12.5px; }
.send { width: 56px; border-radius: 6px; border: none; background: var(--accent); color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; cursor: pointer; }
.send:disabled { opacity: .5; cursor: default; }
.preview { display: flex; align-items: center; gap: 8px; font-size: 11.5px; min-height: 16px; overflow: hidden; white-space: nowrap; }
.preview .hex { color: var(--text); overflow: hidden; text-overflow: ellipsis; }
.preview .sum { color: var(--accent-strong); }
.pop { display: flex; flex-direction: column; gap: 8px; min-width: 220px; }
.opts { display: flex; flex-wrap: wrap; gap: 6px; }
.check { display: flex; align-items: center; gap: 6px; font-size: 12.5px; cursor: pointer; }
.inp { height: 28px; padding: 0 8px; border-radius: 6px; border: 1px solid var(--border); background: var(--bg); color: var(--text); outline: none; font-size: 12.5px; }
</style>
