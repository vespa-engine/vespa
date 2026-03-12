// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        var parsed = RequestBodyParser.parseJson(new ByteArrayInputStream(
                "{\"yql\":\"text\", \"f1\":7.3, \"i1\":7, \"t\":true, \"f\":false, \"n\":null, \"a\":[0.786, 0.193]}".getBytes(StandardCharsets.UTF_8)));
        Map<String, String> m = parsed.stringMap();
        assertEquals(7, m.size());
        assertEquals("text", m.get("yql"));
        assertEquals("7.3", m.get("f1"));
        assertEquals("7", m.get("i1"));
        assertEquals("true", m.get("t"));
        assertEquals("false", m.get("f"));
        assertEquals("null", m.get("n"));
        assertTrue(m.containsKey("a")); // non-structured array serialized as JSON string
        assertTrue(parsed.inspectorMap().isEmpty());
    }

    @Test
    void testThatWeAllowSingleQuotes() {
        var parsed = RequestBodyParser.parseJson(new ByteArrayInputStream("{'yql':'text'}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("text", parsed.stringMap().get("yql"));
    }
}
