<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMessage } from 'naive-ui';
import Icon from './Icon.vue';
import { useSessions } from '../stores/sessions';
import type { Message } from '../types';
import { fmtTime, hexdump } from '../utils';

const props = defineProps<{ uid: string }>();
const sessions = useSessions();
const message = useMessage();
const { t } = useI18n();
const rt = computed(() => sessions.rt(props.uid));
const filter = ref('');
const list = computed(() => {
  const all = rt.value.messages;
  const peer = sessions.activePeer;
  const f = filter.value.trim().toLowerCase();
  return all.filter((m) => (!peer || m.kind === 'sys' || m.peer === peer || m.peer === null) && (!f || m.text.toLowerCase().includes(f) || m.hex.toLowerCase().includes(f)));
});

type Mode = 'auto' | 'text' | 'hex' | 'dump';
const modes = ref<Record<number, Mode>>({});
const MODE_LABEL: Record<Mode, string> = { auto: 'messages.mode.auto', text: 'messages.mode.text', hex: 'messages.mode.hex', dump: 'messages.mode.dump' };
function modeOf(m: Message): Mode {
  return modes.value[m.id] ?? 'auto';
}
function cycle(m: Message) {
  const order: Mode[] = ['auto', 'text', 'hex', 'dump'];
  modes.value[m.id] = order[(order.indexOf(modeOf(m)) + 1) % order.length];
}
function body(m: Message): { text: string; mono: boolean } {
  const mode = modeOf(m);
  if (mode === 'dump') return { text: hexdump(m.hex), mono: true };
  if (mode === 'hex') return { text: m.hex, mono: true };
  if (mode === 'text') return { text: m.isText ? m.text : hexToLatin(m.hex), mono: !m.isText };
  return m.isText ? { text: m.text, mono: false } : { text: m.hex, mono: true };
}
function hexToLatin(hex: string) {
  return hex.split(' ').filter(Boolean).map((h) => { const b = parseInt(h, 16); return b >= 0x20 && b < 0x7f ? String.fromCharCode(b) : '.'; }).join('');
}
async function copy(text: string) {
  await navigator.clipboard.writeText(text);
  message.success(t('common.copied'), { duration: 1200 });
}

const box = ref<HTMLElement | null>(null);
const stick = ref(true);
const pending = ref(0);
function onScroll() {
  const el = box.value!;
  const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
  stick.value = atBottom;
  if (atBottom) pending.value = 0;
}
function toBottom() {
  nextTick(() => { if (box.value) box.value.scrollTop = box.value.scrollHeight; pending.value = 0; stick.value = true; });
}
watch(() => rt.value.messages.length, (n, o) => {
  if (stick.value) toBottom();
  else pending.value += Math.max(0, n - o);
});
watch(() => props.uid, () => { filter.value = ''; toBottom(); });
</script>

<template>
  <div class="wrap">
    <div class="bar">
      <Icon name="filter" size="sm" /><input v-model="filter" :placeholder="t('messages.filter')" />
      <span class="muted" style="font-size:11.5px">{{ t('messages.count', { n: list.length }) }}</span>
    </div>
    <div ref="box" class="stream selectable" @scroll="onScroll">
      <div v-if="!list.length" class="empty">{{ t('messages.empty') }}</div>
      <template v-for="m in list" :key="m.id">
        <div v-if="m.kind === 'sys'" class="sys"><span :class="{ err: m.level === 'error' }">{{ fmtTime(m.ts, false) }} &nbsp;{{ m.text }}</span></div>
        <div v-else class="msg" :class="m.direction">
          <div class="meta muted">
            <span v-if="m.peer && m.direction === 'in'" class="mono">{{ m.peer }}</span>
            <span v-else-if="m.peer" class="mono">→ {{ m.peer }}</span>
            <span>{{ fmtTime(m.ts) }}</span>
            <span>{{ m.len }} B</span>
            <span class="tools">
              <button class="tool"           :title="t('messages.showMode', { mode: t(MODE_LABEL[modeOf(m)]) })" @click="cycle(m)">{{ t(MODE_LABEL[modeOf(m)]) }}</button>
              <button class="tool" :title="t('messages.copyContent')" @click="copy(body(m).text)"><Icon name="copy" size="sm" /></button>
              <button class="tool" :title="t('messages.copyHex')" @click="copy(m.hex)">Hex</button>
            </span>
          </div>
          <div class="bubble" :class="{ mono: body(m).mono }">{{ body(m).text }}</div>
        </div>
      </template>
    </div>
    <button v-if="pending" class="newer" @click="toBottom"><Icon name="arrowDown" size="sm" />{{ t('messages.newer', { n: pending }) }}</button>
  </div>
</template>

<style scoped>
.wrap { flex: 1; min-height: 0; min-width: 0; position: relative; display: flex; flex-direction: column; }
.bar { display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 16px; color: var(--muted); border-bottom: 1px solid var(--border); background: var(--panel); }
.bar input { flex: 1; min-width: 0; border: none; background: transparent; outline: none; color: var(--text); font-family: inherit; font-size: 12.5px; }
.stream { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 16px 20px; display: flex; flex-direction: column; gap: 14px; }
.sys { display: flex; justify-content: center; }
.sys span { font-size: 11.5px; color: var(--muted); background: var(--surface); padding: 3px 10px; border-radius: 10px; }
.sys span.err { color: var(--red); }
.msg { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; max-width: 100%; }
.msg.out { align-items: flex-end; }
.msg > .meta, .msg > .bubble { min-width: 0; }
.meta { display: flex; align-items: center; gap: 8px; font-size: 11px; max-width: 100%; flex-wrap: wrap; }
.tools { display: inline-flex; gap: 2px; opacity: 0; transition: opacity .12s; }
.msg:hover .tools { opacity: 1; }
.tool { display: inline-flex; align-items: center; height: 18px; padding: 0 5px; border-radius: 4px; border: none; background: var(--surface); color: var(--muted); font-size: 10.5px; cursor: pointer; font-family: inherit; }
.tool:hover { color: var(--text); }
.bubble { max-width: min(680px, 90%); padding: 8px 12px; border-radius: 10px; background: var(--bubble-in); font-size: 13px; line-height: 1.55; white-space: pre-wrap; word-break: break-all; }
.bubble.mono { font-family: var(--mono); font-size: 12px; }
.msg.out .bubble { background: var(--bubble-out); }
.newer { position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%); display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 12px; border-radius: 13px; border: 1px solid var(--border); background: var(--panel); color: var(--text); font-size: 12px; cursor: pointer; font-family: inherit; box-shadow: 0 4px 12px rgba(0,0,0,.08); }
</style>
