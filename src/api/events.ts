import { listen, type UnlistenFn } from '@tauri-apps/api/event';
import type { SessionEvent } from '../types';

export function onSessionEvent(handler: (e: SessionEvent) => void): Promise<UnlistenFn> {
  return listen<SessionEvent>('session-event', (ev) => handler(ev.payload));
}
