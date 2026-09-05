import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { api } from '../api/commands';
import type { Kind, SessionConfig } from '../types';

export const useConfigs = defineStore('configs', () => {
  const list = ref<SessionConfig[]>([]);
  const loaded = ref(false);

  async function load() {
    list.value = await api.listSessions();
    loaded.value = true;
  }

  async function save(cfg: SessionConfig): Promise<SessionConfig> {
    const saved = await api.saveSession(cfg);
    const i = list.value.findIndex((c) => c.uid === saved.uid);
    if (i >= 0) list.value[i] = saved;
    else list.value.push(saved);
    return saved;
  }

  async function remove(uids: string[]) {
    await api.deleteSessions(uids);
    list.value = list.value.filter((c) => !uids.includes(c.uid));
  }

  function byKind(kind: Kind) {
    return list.value.filter((c) => c.kind === kind);
  }

  const byUid = computed(() => Object.fromEntries(list.value.map((c) => [c.uid, c])) as Record<string, SessionConfig>);

  return { list, loaded, load, save, remove, byKind, byUid };
});
