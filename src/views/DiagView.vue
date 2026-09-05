<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { NInput, NButton, useMessage } from 'naive-ui';
import Icon from '../components/Icon.vue';
import { api } from '../api/commands';
import type { Interface, PortCheck } from '../types';

const message = useMessage();
const { t } = useI18n();
const ifaces = ref<Interface[]>([]);
const host = ref('127.0.0.1');
const ports = ref('80, 443, 8080');
const checking = ref(false);
const results = ref<PortCheck[]>([]);

async function refresh() { ifaces.value = await api.listInterfaces(); }
onMounted(refresh);
async function copy(text: string) { await navigator.clipboard.writeText(text); message.success(t('common.copied'), { duration: 1200 }); }
function parsePorts(s: string): number[] {
  const out = new Set<number>();
  for (const part of s.split(/[,\s]+/).filter(Boolean)) {
    const m = part.match(/^(\d+)-(\d+)$/);
    if (m) { const a = +m[1], b = +m[2]; for (let p = Math.min(a, b); p <= Math.max(a, b) && out.size < 1024; p++) out.add(p); }
    else if (/^\d+$/.test(part)) out.add(+part);
  }
  return [...out].filter((p) => p > 0 && p < 65536);
}
async function check() {
  const list = parsePorts(ports.value);
  if (!host.value.trim() || !list.length) { message.warning(t('diag.enterHostPort')); return; }
  checking.value = true;
  try { results.value = await api.checkPorts(host.value.trim(), list, 2000); } catch (e) { message.error(String(e)); } finally { checking.value = false; }
}
</script>

<template>
  <div class="diag">
    <section class="col">
      <div class="head"><span class="section-title">{{ t('diag.interfaces') }}</span><button class="icon-btn" @click="refresh"><Icon name="refresh" size="sm" /></button></div>
      <div class="card">
        <div v-for="i in ifaces" :key="i.name + i.ip" class="row">
          <span class="name">{{ i.name }}</span>
          <span class="mono selectable" :class="{ muted: i.isLoopback }">{{ i.ip }}</span>
          <span class="chip" style="height:18px;font-size:10.5px;cursor:default">{{ i.isIpv6 ? 'IPv6' : 'IPv4' }}</span>
          <button class="icon-btn" :title="t('common.copy')" @click="copy(i.ip)"><Icon name="copy" size="sm" /></button>
        </div>
      </div>
    </section>
    <section class="col">
      <div class="head"><span class="section-title">{{ t('diag.portConnectivity') }}</span></div>
      <div class="form">
        <NInput v-model:value="host" class="mono" :placeholder="t('diag.host')" style="width: 220px" />
        <NInput v-model:value="ports" class="mono" :placeholder="t('diag.portsPlaceholder')" @keydown.enter="check" />
        <NButton type="primary" :loading="checking" @click="check">{{ t('diag.check') }}</NButton>
      </div>
      <div class="card" v-if="results.length">
        <div v-for="r in results" :key="r.port" class="row">
          <span class="dot" :class="r.open ? 'on' : 'err'" style="box-shadow:none"></span>
          <span class="mono" style="width:70px">{{ r.port }}</span>
          <span :style="{ color: r.open ? 'var(--green)' : 'var(--muted)', fontWeight: 500, width: '60px' }">{{ r.open ? t('diag.open') : t('diag.closed') }}</span>
          <span class="mono muted">{{ r.elapsedMs }} ms</span>
          <span class="muted" style="font-size:12px;overflow:hidden;text-overflow:ellipsis">{{ r.error ?? '' }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.diag { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; padding: 24px 28px; height: 100%; overflow-y: auto; align-content: start; }
.col { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.head { display: flex; align-items: center; justify-content: space-between; height: 24px; }
.card { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.row { display: flex; align-items: center; gap: 12px; min-height: 38px; padding: 0 12px; border-top: 1px solid var(--border); font-size: 12.5px; }
.row:first-child { border-top: none; }
.row .name { width: 90px; font-weight: 500; flex-shrink: 0; }
.row .mono { flex: 1; }
.form { display: flex; gap: 8px; }
</style>
