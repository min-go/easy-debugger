<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { NModal, NForm, NFormItem, NSelect, NInputNumber, NSwitch, NButton, useMessage } from 'naive-ui';
import { useUi } from '../stores/ui';
import { api } from '../api/commands';
import type { AppSettings } from '../types';

const ui = useUi();
const message = useMessage();
const { t } = useI18n();
const form = ref<AppSettings>({ ...ui.settings });
const dir = ref('');
watch(() => ui.settingsOpen, async (o) => { if (o) { form.value = { ...ui.settings }; dir.value = await api.configDir(); } });
async function save() {
  await ui.save({ ...form.value });
  ui.settingsOpen = false;
  message.success(t('settings.saved'));
}
</script>

<template>
  <NModal :show="ui.settingsOpen" preset="card" :title="t('settings.title')" style="width: 460px" @update:show="ui.settingsOpen = $event">
    <NForm label-placement="left" label-width="110">
      <NFormItem :label="t('settings.theme')"><NSelect v-model:value="form.theme" :options="[{ label: t('settings.system'), value: 'system' }, { label: t('settings.light'), value: 'light' }, { label: t('settings.dark'), value: 'dark' }]" /></NFormItem>
      <NFormItem :label="t('settings.language')"><NSelect v-model:value="form.language" :options="[{ label: t('settings.langSystem'), value: 'system' }, { label: t('settings.langZh'), value: 'zh' }, { label: t('settings.langEn'), value: 'en' }]" /></NFormItem>
      <NFormItem :label="t('settings.maxMessages')"><NInputNumber v-model:value="form.maxMessages" :min="100" :max="100000" :step="500" style="width:100%" /></NFormItem>
      <NFormItem :label="t('settings.fontSize')"><NInputNumber v-model:value="form.fontSize" :min="11" :max="18" style="width:100%" /></NFormItem>
      <NFormItem :label="t('settings.restore')"><NSwitch v-model:value="form.restoreSessions" /></NFormItem>
      <NFormItem :label="t('settings.configDir')"><span class="mono selectable" style="font-size:12px;word-break:break-all">{{ dir }}</span></NFormItem>
    </NForm>
    <template #footer><div style="display:flex;justify-content:flex-end;gap:8px"><NButton @click="ui.settingsOpen = false">{{ t('common.cancel') }}</NButton><NButton type="primary" @click="save">{{ t('common.save') }}</NButton></div></template>
  </NModal>
</template>
