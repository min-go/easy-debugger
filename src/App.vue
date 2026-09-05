<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue';
import { NConfigProvider, NMessageProvider, NDialogProvider, darkTheme, zhCN, dateZhCN, enUS, dateEnUS, type GlobalThemeOverrides } from 'naive-ui';
import { resolveLang } from './i18n';
import DesktopLayout from './layouts/DesktopLayout.vue';
import MobileLayout from './layouts/MobileLayout.vue';
import { useUi } from './stores/ui';
import { useConfigs } from './stores/configs';
import { useSessions } from './stores/sessions';
import { useSnippets } from './stores/snippets';
import { onSessionEvent } from './api/events';
import { api } from './api/commands';

const ui = useUi();
const configs = useConfigs();
const sessions = useSessions();
const snippets = useSnippets();

const naiveLocale = computed(() => (resolveLang(ui.lang) === 'zh' ? zhCN : enUS));
const naiveDate = computed(() => (resolveLang(ui.lang) === 'zh' ? dateZhCN : dateEnUS));
const overrides = computed<GlobalThemeOverrides>(() => {
  const dark = ui.dark;
  const accent = dark ? '#7A93F5' : '#3E63DD';
  const strong = dark ? '#9DB0FA' : '#2F4FB8';
  return {
    common: {
      primaryColor: accent,
      primaryColorHover: strong,
      primaryColorPressed: strong,
      primaryColorSuppl: accent,
      borderRadius: '6px',
      borderRadiusSmall: '4px',
      fontFamily: 'var(--sans)',
      fontFamilyMono: 'var(--mono)',
      fontSize: '13px',
      fontSizeSmall: '12px',
      heightMedium: '32px',
      heightSmall: '26px',
      bodyColor: dark ? '#141518' : '#F5F5F7',
      cardColor: dark ? '#1B1C20' : '#FFFFFF',
      modalColor: dark ? '#1B1C20' : '#FFFFFF',
      popoverColor: dark ? '#25262B' : '#FFFFFF',
      inputColor: dark ? '#1B1C20' : '#FFFFFF',
      borderColor: dark ? '#2B2D33' : '#E5E6EA',
      dividerColor: dark ? '#2B2D33' : '#E5E6EA',
      textColorBase: dark ? '#E9EAEE' : '#1B1C21',
      textColor1: dark ? '#E9EAEE' : '#1B1C21',
      textColor2: dark ? '#E9EAEE' : '#1B1C21',
      textColor3: dark ? '#9A9EAB' : '#6B6F7B',
      placeholderColor: dark ? '#5C606B' : '#B4B7C0',
      successColor: dark ? '#4CC46A' : '#2EAE48',
      errorColor: dark ? '#EF5F63' : '#DE3E42',
      warningColor: dark ? '#E8B043' : '#D9950E',
    },
  };
});

let unlisten: (() => void) | null = null;
onMounted(async () => {
  unlisten = await onSessionEvent((e) => sessions.handle(e));
  await Promise.all([ui.load(), configs.load(), snippets.load()]);
  const running = await api.runningSessions();
  for (const uid of running) await sessions.sync(uid);
});
onUnmounted(() => unlisten?.());
</script>

<template>
  <NConfigProvider :theme="ui.dark ? darkTheme : null" :theme-overrides="overrides" :locale="naiveLocale" :date-locale="naiveDate">
    <NMessageProvider placement="bottom-right">
      <NDialogProvider>
        <DesktopLayout v-if="ui.platform === 'desktop'" />
        <MobileLayout v-else />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
