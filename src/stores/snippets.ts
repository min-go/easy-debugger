import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api/commands';
import type { Snippet } from '../types';

export const useSnippets = defineStore('snippets', () => {
  const list = ref<Snippet[]>([]);
  async function load() {
    list.value = await api.listSnippets();
  }
  async function save(s: Snippet) {
    const saved = await api.saveSnippet(s);
    const i = list.value.findIndex((x) => x.uid === saved.uid);
    if (i >= 0) list.value[i] = saved;
    else list.value.push(saved);
    return saved;
  }
  async function remove(uids: string[]) {
    await api.deleteSnippets(uids);
    list.value = list.value.filter((x) => !uids.includes(x.uid));
  }
  return { list, load, save, remove };
});
