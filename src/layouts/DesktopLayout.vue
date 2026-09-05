<script setup lang="ts">
import { computed } from 'vue';
import Icon from '../components/Icon.vue';
import SessionsView from '../views/SessionsView.vue';
import DnsView from '../views/DnsView.vue';
import DiagView from '../views/DiagView.vue';
import SnippetsView from '../views/SnippetsView.vue';
import SettingsDialog from '../components/SettingsDialog.vue';
import { useI18n } from 'vue-i18n';
import { useUi, type View } from '../stores/ui';
import { useSessions } from '../stores/sessions';
import { fmtBytes } from '../utils';

const ui = useUi();
const sessions = useSessions();
const { t } = useI18n();
const nav: { key: View; label: string; icon: string }[] = [
  { key: 'sessions', label: 'nav.sessions', icon: 'sessions' },
  { key: 'dns', label: 'nav.dns', icon: 'dns' },
  { key: 'diag', label: 'nav.diag', icon: 'diag' },
  { key: 'snippets', label: 'nav.snippets', icon: 'snippets' },
];
const activeCount = computed(() => Object.values(sessions.runtime).filter((r) => r.online).length);
const totals = computed(() => {
  let i = 0, o = 0;
  for (const r of Object.values(sessions.runtime)) { i += r.bytesIn; o += r.bytesOut; }
  return { i, o };
});
</script>

<template>
  <div class="shell">
    <header class="topbar" data-tauri-drag-region>
      <div class="brand"><Icon name="logo" /><span>Easy Debugger</span></div>
      <nav class="nav">
        <button v-for="n in nav" :key="n.key" class="item" :class="{ active: ui.view === n.key }" @click="ui.view = n.key">
          <Icon :name="n.icon" /><span>{{ t(n.label) }}</span>
        </button>
      </nav>
      <div class="spacer"></div>
      <button class="icon-btn" :title="t('nav.settings')" @click="ui.settingsOpen = true"><Icon name="settings" /></button>
    </header>
    <main class="main">
      <SessionsView v-show="ui.view === 'sessions'" />
      <DnsView v-if="ui.view === 'dns'" />
      <DiagView v-if="ui.view === 'diag'" />
      <SnippetsView v-if="ui.view === 'snippets'" />
    </main>
    <footer class="statusbar">
      <span class="mono">↑ {{ fmtBytes(totals.o) }}</span>
      <span class="mono">↓ {{ fmtBytes(totals.i) }}</span>
      <span>{{ t('nav.activeSessions', { n: activeCount }) }}</span>
      <span class="spacer"></span>
      <span>v1.0.0</span>
    </footer>
    <SettingsDialog />
  </div>
</template>

<style scoped>
.shell { height: 100%; display: flex; flex-direction: column; }
.topbar { height: 44px; display: flex; align-items: center; gap: 8px; padding: 0 12px 0 84px; border-bottom: 1px solid var(--border); background: var(--panel); flex-shrink: 0; }
.brand { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 13.5px; }
.brand .icon { stroke: var(--accent); }
.nav { display: flex; align-items: center; gap: 2px; margin-left: 20px; }
.nav .item { display: inline-flex; align-items: center; gap: 6px; height: 28px; padding: 0 10px; border-radius: 6px; color: var(--muted); font-weight: 500; border: none; background: transparent; cursor: pointer; font-family: inherit; font-size: 13px; }
.nav .item:hover { color: var(--text); }
.nav .item.active { background: var(--surface); color: var(--text); }
.spacer { flex: 1; }
.main { flex: 1; min-height: 0; display: flex; }
.main > * { flex: 1; min-width: 0; }
.statusbar { height: 26px; display: flex; align-items: center; gap: 16px; padding: 0 14px; border-top: 1px solid var(--border); background: var(--panel); font-size: 11.5px; color: var(--muted); flex-shrink: 0; }
</style>
