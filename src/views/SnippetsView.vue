<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { NInput, NSelect, NButton, useMessage, useDialog } from 'naive-ui';
import Icon from '../components/Icon.vue';
import { useSnippets } from '../stores/snippets';
import type { Format, Snippet } from '../types';

const snippets = useSnippets();
const message = useMessage();
const dialog = useDialog();
const { t } = useI18n();
const filter = ref('');
const editing = ref<Snippet | null>(null);
const fmtOpts = computed(() => [{ label: t('format.text'), value: 'text' }, { label: t('format.hex'), value: 'hex' }, { label: t('format.base64'), value: 'base64' }, { label: t('format.json'), value: 'json' }]);
const list = computed(() => snippets.list.filter((s) => !filter.value || s.name.includes(filter.value) || s.group.includes(filter.value) || s.content.includes(filter.value)));
const groups = computed(() => { const m = new Map<string, Snippet[]>(); for (const s of list.value) { const g = s.group || t('snippets.ungrouped'); if (!m.has(g)) m.set(g, []); m.get(g)!.push(s); } return [...m.entries()]; });

function add() { editing.value = { uid: '', name: '', group: '', format: 'text' as Format, content: '' }; }
function edit(s: Snippet) { editing.value = { ...s }; }
async function save() {
  if (!editing.value) return;
  try { await snippets.save(editing.value); editing.value = null; message.success(t('settings.saved')); } catch (e) { message.error(String(e)); }
}
function remove(s: Snippet) {
  dialog.warning({ title: t('snippets.deleteTitle'), content: t('snippets.deleteConfirm', { name: s.name }), positiveText: t('common.delete'), negativeText: t('common.cancel'), onPositiveClick: () => snippets.remove([s.uid]) });
}
async function copy(text: string) { await navigator.clipboard.writeText(text); message.success(t('common.copied'), { duration: 1200 }); }
</script>

<template>
  <div class="snip">
    <aside class="side">
      <div class="top"><NInput v-model:value="filter" size="small" :placeholder="t('snippets.filter')" clearable /><NButton size="small" type="primary" @click="add"><template #icon><Icon name="plus" size="sm" /></template>{{ t('snippets.new') }}</NButton></div>
      <div class="list">
        <div v-for="[g, items] in groups" :key="g" class="group">
          <div class="section-title" style="padding:6px 10px">{{ g }}</div>
          <button v-for="s in items" :key="s.uid" class="row" :class="{ active: editing?.uid === s.uid }" @click="edit(s)">
            <span class="name">{{ s.name }}</span><span class="chip" style="height:18px;font-size:10.5px">{{ fmtOpts.find((f) => f.value === s.format)?.label }}</span>
          </button>
        </div>
        <div v-if="!list.length" class="faint" style="padding:12px;font-size:12px">{{ t('snippets.empty') }}</div>
      </div>
    </aside>
    <main class="main">
      <div v-if="!editing" class="empty"><Icon name="snippets" style="width:28px;height:28px;stroke:var(--faint)" /><span>{{ t('snippets.selectOrNew') }}</span></div>
      <div v-else class="editor">
        <div class="two"><div class="field"><span class="label">{{ t('snippets.name') }}</span><NInput v-model:value="editing.name" /></div><div class="field"><span class="label">{{ t('snippets.group') }}</span><NInput v-model:value="editing.group" :placeholder="t('snippets.groupOptional')" /></div><div class="field"><span class="label">{{ t('snippets.format') }}</span><NSelect v-model:value="editing.format" :options="fmtOpts" /></div></div>
        <div class="field" style="flex:1"><span class="label">{{ t('snippets.content') }}</span><NInput v-model:value="editing.content" type="textarea" class="mono" :autosize="{ minRows: 8 }" :placeholder="t('snippets.contentPlaceholder')" /></div>
        <div class="foot">
          <NButton v-if="editing.uid" quaternary type="error" @click="remove(editing)">{{ t('common.delete') }}</NButton>
          <span style="flex:1"></span>
          <NButton quaternary @click="copy(editing.content)">{{ t('snippets.copyContent') }}</NButton>
          <NButton quaternary @click="editing = null">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" @click="save">{{ t('common.save') }}</NButton>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.snip { display: flex; height: 100%; }
.side { width: 300px; flex-shrink: 0; border-right: 1px solid var(--border); background: var(--panel); display: flex; flex-direction: column; }
.top { display: flex; gap: 8px; padding: 12px 12px 8px; }
.list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.row { display: flex; align-items: center; gap: 8px; width: 100%; height: 34px; padding: 0 10px; border-radius: 6px; border: none; background: transparent; cursor: pointer; font-family: inherit; color: var(--text); font-size: 13px; }
.row:hover, .row.active { background: var(--surface); }
.row .name { flex: 1; text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.main { flex: 1; min-width: 0; padding: 24px 28px; }
.editor { display: flex; flex-direction: column; gap: 14px; height: 100%; max-width: 760px; }
.two { display: grid; grid-template-columns: 1fr 1fr 140px; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.foot { display: flex; gap: 8px; }
</style>
