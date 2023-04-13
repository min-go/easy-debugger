package io.dengliming.easydebugger.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum SessionHolder {
    INSTANCE;

    private static final Map<String, ClientSession> SESSION_MAP = new ConcurrentHashMap<>();

    public ClientSession get(String key) {
        return SESSION_MAP.get(key);
    }

    public synchronized ClientSession put(String key, ClientSession value) {
        return SESSION_MAP.put(key, value);
    }

    public synchronized ClientSession remove(String key) {
        return SESSION_MAP.remove(key);
    }

}
