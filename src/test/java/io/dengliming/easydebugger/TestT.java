package io.dengliming.easydebugger;

import io.dengliming.easydebugger.utils.T;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestT {

    @Test
    public void TestHexString() {
        assertTrue(T.isHexString("0123456789ABCDEF"));
        assertFalse(T.isHexString("0123456789ABCDEFG"));
        assertTrue(T.isHexString("AB"));
    }

    @Test
    public void TestMessyCode() {
        assertTrue(T.isMessyCode("Դ\u03A2���"));
        assertFalse(T.isMessyCode("测试测试f123123`~!@:/.asdf!#$%^&*()_+-=[]{};':\",.<>/?\\|/*-+."));
        assertTrue(T.isMessyCode("�123测试"));
    }
}
