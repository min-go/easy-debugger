<script setup lang="ts">
import { computed, ref } from 'vue';
import ConfigSidebar from '../components/ConfigSidebar.vue';
import ConfigDialog from '../components/ConfigDialog.vue';
import SessionHeader from '../components/SessionHeader.vue';
import MessageList from '../components/MessageList.vue';
import SendBar from '../components/SendBar.vue';
import InfoPanel from '../components/InfoPanel.vue';
import { useI18n } from 'vue-i18n';
import Icon from '../components/Icon.vue';
import { useConfigs } from '../stores/configs';
import { useSessions } from '../stores/sessions';
import { useUi } from '../stores/ui';
import type { Kind, SessionConfig } from '../types';

const configs = useConfigs();
const sessions = useSessions();
const ui = useUi();
const { t } = useI18n();

const active = computed(() => (sessions.active ? configs.byUid[sessions.active] ?? null : null));
const dialog = ref<{ open: boolean; config: SessionConfig | null; kind: Kind }>({ open: false, config: null, kind: 'tcpClient' });

function openNew(kind: Kind = 'tcpClient') {
  dialog.value = { open: true, config: null, kind };
}
function openEdit(cfg: SessionConfig) {
  dialog.value = { open: true, config: cfg, kind: cfg.kind };
}
</script>

<template>
  <div class="sessions">
    <ConfigSidebar @new="openNew" @edit="openEdit" />
    <div class="center" v-if="active">
      <SessionHeader :config="active" @edit="openEdit(active)" />
      <MessageList :uid="active.uid" />
      <SendBar :config="active" />
    </div>
    <div class="center empty" v-else>
      <Icon name="sessions" style="width:28px;height:28px;stroke:var(--faint)" />
      <span>{{ t('sidebar.selectOrNew') }}</span>
      <button class="chip" @click="openNew()"><Icon name="plus" size="sm" />{{ t('sidebar.newSession') }}</button>
    </div>
    <InfoPanel v-if="active && ui.panelOpen" :config="active" />
    <ConfigDialog v-model:open="dialog.open" :config="dialog.config" :kind="dialog.kind" />
  </div>
</template>

<style scoped>
.sessions { display: flex; height: 100%; min-width: 0; }
.center { flex: 1; min-width: 0; display: flex; flex-direction: column; background: var(--bg); }
</style>
