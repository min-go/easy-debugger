<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { NDropdown, useDialog, useMessage } from 'naive-ui';
import Icon from './Icon.vue';
import { useConfigs } from '../stores/configs';
import { useSessions } from '../stores/sessions';
import { KIND_GROUPS, type Kind, type SessionConfig } from '../types';
import { api } from '../api/commands';

const emit = defineEmits<{ new: [kind: Kind]; edit: [cfg: SessionConfig] }>();
const configs = useConfigs();
const sessions = useSessions();
const dialog = useDialog();
const message = useMessage();
const { t } = useI18n();
const filter = ref('');
const collapsed = ref<Record<string, boolean>>({});

const groups = computed(() =>
  KIND_GROUPS.map((g) => ({ ...g, items: configs.byKind(g.kind).filter((c) => !filter.value || c.name.includes(filter.value) || c.host.includes(filter.value)) })).filter((g) => g.items.length || !filter.value),
);

function state(uid: string) {
  const r = sessions.runtime[uid];
  if (!r) return '';
  if (r.online) return 'on';
  if (r.reconnecting) return 'warn';
  return '';
}

function addr(c: SessionConfig) {
  if (c.kind === 'udp') return c.host ? `${c.host}:${c.port} · ${t('common.local')} ${c.localPort || t('common.auto')}` : `${t('common.local')} ${c.localPort || t('common.auto')}`;
  if (c.kind === 'wsClient' && c.host.startsWith('ws')) return c.host;
  return `${c.host}:${c.port}${c.kind.startsWith('ws') && c.wsPath !== '/' ? c.wsPath : ''}`;
}

const menuFor = ref<SessionConfig | null>(null);
const menuPos = ref({ x: 0, y: 0 });
const menuOpen = ref(false);
const menuOptions = computed(() => [
  { label: t('common.edit'), key: 'edit' },
  { label: t('common.duplicate'), key: 'dup' },
  { label: t('common.delete'), key: 'del' },
]);
function onContext(e: MouseEvent, c: SessionConfig) {
  e.preventDefault();
  menuFor.value = c;
  menuPos.value = { x: e.clientX, y: e.clientY };
  menuOpen.value = true;
}
async function onMenu(key: string) {
  menuOpen.value = false;
  const c = menuFor.value;
  if (!c) return;
  if (key === 'edit') emit('edit', c);
  if (key === 'dup') {
    const copy = { ...JSON.parse(JSON.stringify(c)), uid: '', name: `${c.name} ${t('common.duplicate')}` };
    await configs.save(copy);
    message.success(t('common.copied'));
  }
  if (key === 'del') confirmDelete(c);
}

function confirmDelete(c: SessionConfig) {
  dialog.warning({
    title: t('session.deleteTitle'),
    content: t('session.deleteConfirm', { name: c.name }),
    positiveText: t('common.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await configs.remove([c.uid]);
        sessions.drop(c.uid);
      } catch (e) {
        message.error(String(e));
      }
    },
  });
}

async function toggle(c: SessionConfig) {
  const r = sessions.rt(c.uid);
  try {
    if (r.online || r.reconnecting) await api.stopSession(c.uid);
    else await api.startSession(c.uid);
  } catch (e) {
    message.error(String(e));
    sessions.sys(c.uid, String(e), 'error');
  }
}
</script>

<template>
  <aside class="sidebar">
    <div class="search">
      <Icon name="search" size="sm" />
      <input v-model="filter" :placeholder="t('sidebar.filter')" />
    </div>
    <div class="list">
      <div v-for="g in groups" :key="g.kind" class="group">
        <button class="ghead" @click="collapsed[g.kind] = !collapsed[g.kind]">
          <Icon :name="collapsed[g.kind] ? 'chevronR' : 'chevron'" size="sm" />
          <span class="section-title">{{ t(`kind.${g.kind}`) }}</span>
          <span class="count">{{ g.items.length }}</span>
          <span class="spacer"></span>
          <span class="add icon-btn" :title="t('sidebar.newSession')" @click.stop="emit('new', g.kind)"><Icon name="plus" size="sm" /></span>
        </button>
        <template v-if="!collapsed[g.kind]">
          <div
            v-for="c in g.items"
            :key="c.uid"
            class="item"
            :class="{ active: sessions.active === c.uid }"
            @click="sessions.select(c.uid)"
            @dblclick="toggle(c)"
            @contextmenu="onContext($event, c)"
          >
            <span class="dot" :class="state(c.uid)"></span>
            <div class="text">
              <span class="name">{{ c.name }}</span>
              <span class="addr mono">{{ addr(c) }}</span>
            </div>
            <span v-if="sessions.runtime[c.uid]?.unread" class="badge">{{ sessions.runtime[c.uid].unread }}</span>
            <button class="del icon-btn" :title="t('common.delete')" @click.stop="confirmDelete(c)"><Icon name="trash" size="sm" /></button>
          </div>
          <div v-if="!g.items.length" class="none">{{ t('sidebar.noConfig') }}</div>
        </template>
      </div>
    </div>
    <div class="foot">
      <button class="newbtn" @click="emit('new', 'tcpClient')"><Icon name="plus" size="sm" />{{ t('sidebar.newSession') }}</button>
    </div>
    <NDropdown :show="menuOpen" :options="menuOptions" :x="menuPos.x" :y="menuPos.y" placement="bottom-start" trigger="manual" @select="onMenu" @clickoutside="menuOpen = false" />
  </aside>
</template>

<style scoped>
.sidebar { width: 250px; flex-shrink: 0; display: flex; flex-direction: column; border-right: 1px solid var(--border); background: var(--panel); }
.search { display: flex; align-items: center; gap: 6px; margin: 10px 10px 4px; height: 28px; padding: 0 8px; border-radius: 6px; background: var(--surface); color: var(--muted); }
.search input { flex: 1; min-width: 0; border: none; background: transparent; outline: none; color: var(--text); font-family: inherit; font-size: 12.5px; }
.list { flex: 1; overflow-y: auto; padding: 6px 8px; display: flex; flex-direction: column; gap: 10px; }
.ghead { display: flex; align-items: center; gap: 6px; height: 26px; padding: 0 6px; border: none; background: transparent; color: var(--muted); cursor: pointer; width: 100%; font-family: inherit; }
.ghead .count { font-size: 11px; color: var(--faint); }
.ghead .add { width: 22px; height: 22px; opacity: 0; }
.ghead:hover .add { opacity: 1; }
.spacer { flex: 1; }
.item { display: flex; align-items: center; gap: 10px; height: 44px; padding: 0 10px; border-radius: 6px; cursor: pointer; }
.item:hover { background: var(--surface); }
.item.active { background: var(--surface); }
.item .text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.item .name { font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item .addr { font-size: 11px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item .del { width: 24px; height: 24px; opacity: 0; flex-shrink: 0; }
.item:hover .del { opacity: 1; }
.item .del:hover { color: var(--red); }
.badge { min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: var(--accent); color: #fff; font-size: 10.5px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; }
.none { padding: 6px 10px 2px; font-size: 12px; color: var(--faint); }
.foot { padding: 10px 12px; border-top: 1px solid var(--border); }
.newbtn { width: 100%; height: 30px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; border-radius: 6px; border: 1px solid var(--border); background: var(--panel); color: var(--text); font-weight: 500; cursor: pointer; font-family: inherit; font-size: 13px; }
.newbtn:hover { background: var(--surface); }
</style>
