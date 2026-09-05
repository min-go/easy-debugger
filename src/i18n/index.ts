import { createI18n } from 'vue-i18n';
import zh from './zh';
import en from './en';

export type Lang = 'system' | 'zh' | 'en';

export function resolveLang(setting: Lang): 'zh' | 'en' {
  if (setting === 'zh' || setting === 'en') return setting;
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en';
}

export const i18n = createI18n({
  legacy: false,
  locale: 'zh',
  fallbackLocale: 'en',
  messages: { zh, en },
});

export function setLang(setting: Lang) {
  i18n.global.locale.value = resolveLang(setting);
}

export const t = i18n.global.t;
