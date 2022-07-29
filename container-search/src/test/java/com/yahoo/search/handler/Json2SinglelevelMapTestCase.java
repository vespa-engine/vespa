package com.yahoo.search.handler;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Json2SinglelevelMapTestCase {
    @Test
    void testDecodeString() {
        Map<String, String> m = new Json2SingleLevelMap(new ByteArrayInputStream("{\"yql\":\"text\", \"f1\":7.3, \"i1\":7, \"t\":true, \"f\":false,  \"n\":null, \"a\":[0.786, 0.193]}".getBytes(StandardCharsets.UTF_8))).parse();
        assertEquals(7, m.size());
        assertTrue(m.containsKey("yql"));
        assertTrue(m.containsKey("f1"));
        assertTrue(m.containsKey("i1"));
        assertTrue(m.containsKey("t"));
        assertTrue(m.containsKey("f"));
        assertTrue(m.containsKey("n"));
        assertTrue(m.containsKey("a"));
        assertEquals("text", m.get("yql"));
        assertEquals("7.3", m.get("f1"));
        assertEquals("7", m.get("i1"));
        assertEquals("true", m.get("t"));
        assertEquals("false", m.get("f"));
        assertEquals("null", m.get("n"));
        assertEquals("[0.786, 0.193]", m.get("a"));
    }

    @Test
    void testThatWeAllowSingleQuotes() {
        Map<String, String> m = new Json2SingleLevelMap(new ByteArrayInputStream("{'yql':'text'}".getBytes(StandardCharsets.UTF_8))).parse();
        assertTrue(m.containsKey("yql"));
        assertEquals("text", m.get("yql"));
    }
}
