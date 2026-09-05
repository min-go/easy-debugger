import { defineStore } from 'pinia';
import { computed, ref, watchEffect } from 'vue';
import { platform as osPlatform } from '@tauri-apps/plugin-os';
import { setLang, type Lang } from '../i18n';
import { useOsTheme } from 'naive-ui';
import { api } from '../api/commands';
import type { AppSettings } from '../types';
import { MAX_MESSAGES } from './sessions';

export type View = 'sessions' | 'dns' | 'diag' | 'snippets';
export type Platform = 'desktop' | 'mobile';

export const useUi = defineStore('ui', () => {
  const view = ref<View>('sessions');
  const platform = ref<Platform>('desktop');
  async function detectPlatform() {
    // Preview override for browser-based screenshots; ignored in the real app.
    const forced = new URLSearchParams(location.search).get('platform');
    if (forced === 'mobile' || forced === 'desktop') { platform.value = forced; return; }
    try {
      const p = await osPlatform();
      platform.value = p === 'android' || p === 'ios' ? 'mobile' : 'desktop';
      document.documentElement.setAttribute('data-os', p);
    } catch {
      // Not running under Tauri (e.g. plain browser): fall back to a touch + narrow-screen heuristic.
      platform.value = navigator.maxTouchPoints > 0 && Math.min(screen.width, screen.height) < 640 ? 'mobile' : 'desktop';
      document.documentElement.setAttribute('data-os', 'web');
    }
  }
  const os = useOsTheme();
  const settings = ref<AppSettings>({ theme: 'system', language: 'system', maxMessages: 5000, fontSize: 13, restoreSessions: false });
  const dark = computed(() => (settings.value.theme === 'system' ? os.value === 'dark' : settings.value.theme === 'dark'));
  const lang = computed(() => settings.value.language);
  const panelOpen = ref(true);
  const settingsOpen = ref(false);

  async function load() {
    await detectPlatform();
    settings.value = await api.getSettings();
    setLang(settings.value.language as Lang);
  }
  async function save(s: AppSettings) {
    settings.value = s;
    setLang(s.language as Lang);
    await api.saveSettings(s);
  }

  watchEffect(() => {
    document.documentElement.classList.toggle('dark', dark.value);
    document.documentElement.style.fontSize = `${settings.value.fontSize}px`;
    MAX_MESSAGES.value = settings.value.maxMessages;
  });

  return { view, platform, dark, lang, settings, panelOpen, settingsOpen, load, save };
});
