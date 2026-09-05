<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { NInput, NSelect, NButton, useMessage } from 'naive-ui';
import Icon from '../components/Icon.vue';
import { api } from '../api/commands';
import type { DnsResult } from '../types';

const message = useMessage();
const { t } = useI18n();
const name = ref('');
const type = ref('A');
const server = ref<string>('');
const TYPES = ['A', 'AAAA', 'CNAME', 'MX', 'TXT', 'NS', 'SOA', 'SRV', 'PTR'];
const SERVERS = computed(() => [
  { label: t('dns.systemDefault'), value: '' },
  { label: '8.8.8.8 · Google', value: '8.8.8.8' },
  { label: '1.1.1.1 · Cloudflare', value: '1.1.1.1' },
  { label: `223.5.5.5 · ${t('dns.ali')}`, value: '223.5.5.5' },
  { label: '114.114.114.114', value: '114.114.114.114' },
  { label: `119.29.29.29 · ${t('dns.tencent')}`, value: '119.29.29.29' },
]);
const custom = ref('');
const compare = ref(false);
const loading = ref(false);
const results = ref<(DnsResult | { error: string; server: string })[]>([]);
const history = ref<{ name: string; type: string; at: number }[]>([]);

async function run() {
  const q = name.value.trim();
  if (!q) return;
  loading.value = true;
  results.value = [];
  const base = custom.value.trim() || server.value || null;
  const servers: (string | null)[] = compare.value ? [base, '8.8.8.8', '1.1.1.1', '223.5.5.5'].filter((v, i, a) => a.indexOf(v) === i) : [base];
  try {
    const rs = await Promise.all(servers.map((s) => api.dnsQuery(q, type.value, s).catch((e) => ({ error: String(e), server: s ?? t('dns.systemDefault') }))));
    results.value = rs;
    if (!history.value.find((h) => h.name === q && h.type === type.value)) history.value.unshift({ name: q, type: type.value, at: Date.now() });
    if (history.value.length > 30) history.value.length = 30;
  } finally {
    loading.value = false;
  }
}
function recall(h: { name: string; type: string }) { name.value = h.name; type.value = h.type; void run(); }
async function copy(text: string) { await navigator.clipboard.writeText(text); message.success(t('common.copied'), { duration: 1200 }); }
function ago(ts: number) { const s = Math.floor((Date.now() - ts) / 1000); return s < 60 ? t('dns.justNow') : s < 3600 ? t('dns.minutesAgo', { n: Math.floor(s / 60) }) : t('dns.hoursAgo', { n: Math.floor(s / 3600) }); }
</script>

<template>
  <div class="dns">
    <aside class="side">
      <div class="form">
        <div class="field"><span class="label">{{ t('dns.domainOrIp') }}</span><NInput v-model:value="name" class="mono" placeholder="example.com" @keydown.enter="run" /></div>
        <div class="field"><span class="label">{{ t('dns.recordType') }}</span><div class="types"><button v-for="t in TYPES" :key="t" class="chip mono" :class="{ active: type === t }" @click="type = t">{{ t }}</button></div></div>
        <div class="field"><span class="label">{{ t('dns.server') }}</span><NSelect v-model:value="server" :options="SERVERS" /><NInput v-model:value="custom" class="mono" size="small" :placeholder="t('dns.customServer')" /></div>
        <label class="check"><input type="checkbox" v-model="compare" /> {{ t('dns.compare') }}</label>
        <NButton type="primary" block :loading="loading" @click="run">{{ t('dns.query') }}</NButton>
      </div>
      <div class="hist">
        <div class="section-title" style="padding:4px 10px 8px">{{ t('dns.history') }}</div>
        <button v-for="h in history" :key="h.name + h.type" class="hrow" @click="recall(h)"><span class="mono">{{ h.name }}</span><span class="chip mono" style="height:18px;font-size:10.5px">{{ h.type }}</span><span class="faint">{{ ago(h.at) }}</span></button>
        <div v-if="!history.length" class="faint" style="padding:0 10px;font-size:12px">{{ t('dns.noHistory') }}</div>
      </div>
    </aside>
    <main class="main">
      <div v-if="!results.length" class="empty"><Icon name="dns" style="width:28px;height:28px;stroke:var(--faint)" /><span>{{ t('dns.startHint') }}</span></div>
      <div v-for="(r, i) in results" :key="i" class="result">
        <template v-if="'error' in r">
          <div class="rhead"><span class="mono" style="font-size:16px;font-weight:500">{{ name }}</span><span class="chip mono">{{ type }}</span><span class="spacer"></span><span class="muted" style="font-size:12px">{{ r.server }}</span></div>
          <div class="card" style="padding:14px;color:var(--red)">{{ r.error }}</div>
        </template>
        <template v-else>
          <div class="rhead">
            <span class="mono" style="font-size:16px;font-weight:500">{{ r.query }}</span><span class="chip mono">{{ r.recordType }}</span>
            <span class="spacer"></span>
            <span class="muted" style="font-size:12px">{{ r.server }} · <span class="mono" style="color:var(--text)">{{ r.elapsedMs }} ms</span></span>
            <button class="chip" @click="copy(r.records.map((x) => x.value).join('\n'))"><Icon name="copy" size="sm" />{{ t('common.copy') }}</button>
          </div>
          <div class="card">
            <div class="trow th"><span>{{ t('dns.colType') }}</span><span>{{ t('dns.colName') }}</span><span>{{ t('dns.colValue') }}</span><span style="text-align:right">{{ t('dns.colTtl') }}</span></div>
            <div v-for="(rec, j) in r.records" :key="j" class="trow selectable"><span><span class="chip mono" style="height:20px;font-size:11px;cursor:default">{{ rec.recordType }}</span></span><span class="mono muted">{{ rec.name }}</span><span class="mono">{{ rec.value }}</span><span class="mono muted" style="text-align:right">{{ rec.ttl }}</span></div>
            <div v-if="!r.records.length" class="trow faint">{{ t('dns.noRecords') }}</div>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>

<style scoped>
.dns { display: flex; height: 100%; }
.side { width: 340px; flex-shrink: 0; border-right: 1px solid var(--border); background: var(--panel); display: flex; flex-direction: column; }
.form { padding: 18px 18px 16px; display: flex; flex-direction: column; gap: 14px; border-bottom: 1px solid var(--border); }
.field { display: flex; flex-direction: column; gap: 6px; }
.types { display: flex; flex-wrap: wrap; gap: 6px; }
.check { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted); cursor: pointer; }
.hist { flex: 1; overflow-y: auto; padding: 12px 8px; display: flex; flex-direction: column; gap: 2px; }
.hrow { display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 10px; border-radius: 6px; border: none; background: transparent; cursor: pointer; font-family: inherit; color: var(--text); font-size: 12px; }
.hrow:hover { background: var(--surface); }
.hrow .mono { flex: 1; text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hrow .faint { font-size: 11px; }
.main { flex: 1; min-width: 0; overflow-y: auto; padding: 22px 26px; display: flex; flex-direction: column; gap: 20px; }
.result { display: flex; flex-direction: column; gap: 12px; }
.rhead { display: flex; align-items: center; gap: 10px; }
.spacer { flex: 1; }
.card { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.trow { display: grid; grid-template-columns: 70px 220px minmax(0, 1fr) 70px; gap: 12px; align-items: center; min-height: 36px; padding: 6px 12px; border-top: 1px solid var(--border); font-size: 12.5px; word-break: break-all; }
.trow.th { border-top: none; min-height: 30px; font-size: 11.5px; color: var(--muted); font-weight: 500; }
</style>
