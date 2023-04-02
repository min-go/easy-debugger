package io.dengliming.easydebugger.netty;

import java.util.HashMap;
import java.util.Map;

public enum MsgType {
    /**
     * 字符串
     */
    STRING("字符串"),
    /**
     * 16进制
     */
    HEX("16进制");

    private String name;

    MsgType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private final static Map<String, MsgType> mappingNameMap = new HashMap<>(2);
    static {
        for (MsgType value : MsgType.values()) {
            mappingNameMap.put(value.getName(), value);
        }
    }

    public static MsgType getByName(String name) {
        return mappingNameMap.get(name);
    }
}
