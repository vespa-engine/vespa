// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.slime.SlimeDataSink;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses slime utils and slime adapter to make inspectors from JSON. Then, the slime data
 * sink is used to convert this back to slime. These tests check that arrays and objects
 * distribute as expected and leafs are preserved.
 *
 * @author johsol
 */
public class InspectorDataSourceTest {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                () -> "Expected " + SlimeUtils.toJson(expected) +
                        " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testValuesInObjectIsPreserved() {
        var expected = SlimeUtils.jsonToSlime("{ int: 1024, " +
                "  bool: true," +
                "  double: 3.5," +
                "  my_null: null," +
                "  string: 'hello' }");

        var inspector = new SlimeAdapter(expected.get());
        var actual = SlimeDataSink.buildSlime(inspector);
        assertSlime(expected, actual);
    }

    @Test
    public void testValuesInArrayIsPreserved() {
        var expected = SlimeUtils.jsonToSlime("[1, true, 2.5, 'foo', null]");

        var inspector = new SlimeAdapter(expected.get());
        var actual = SlimeDataSink.buildSlime(inspector);
        assertSlime(expected, actual);
    }

    @Test
    public void testNestedObjectAndArrayArePreserved() {
        var expected = SlimeUtils.jsonToSlime("{ nums: [1, 2], meta: { ok: true } }");

        var inspector = new SlimeAdapter(expected.get());
        var actual = SlimeDataSink.buildSlime(inspector);
        assertSlime(expected, actual);
    }

    @Test
    public void testArrayOfObjectsArePreserved() {
        var expected = SlimeUtils.jsonToSlime("[ { id: 1 }, { id: 2 } ]");

        var inspector = new SlimeAdapter(expected.get());
        var actual = SlimeDataSink.buildSlime(inspector);
        assertSlime(expected, actual);
    }

    @Test
    public void testBinaryDataIsPreserved() {
        byte[] bytes = new byte[]{0, 1, 2, (byte) 0xFF};
        Inspector inspector = new Value.DataValue(bytes);
        Slime actual = SlimeDataSink.buildSlime(inspector);

        Slime expected = new Slime();
        expected.setData(bytes);
        assertSlime(expected, actual);
    }

    /**
     * Captures the utf8 value from string value.
     */
    static class Utf8CaptureSink implements DataSink {
        byte[] lastUtf8;

        public void stringValue(String u16, byte[] u8) {
            lastUtf8 = u8;
        }

        public void fieldName(String utf16, byte[] utf8) { /* no-op */ }

        public void fieldName(String u16) { /* no-op */ }

        public void startObject() { /* no-op */ }

        public void endObject() { /* no-op */ }

        public void startArray() { /* no-op */ }

        public void endArray() { /* no-op */ }

        public void emptyValue() { /* no-op */ }

        public void booleanValue(boolean v) { /* no-op */ }

        public void longValue(long v) { /* no-op */ }

        public void doubleValue(double v) { /* no-op */ }

        public void dataValue(byte[] d) { /* no-op */ }

        public void stringValue(String u16) { /* no-op */ }
    }

    @Test
    public void testUtf8StringIsPreserved() {
        byte[] utf8 = "hello world".getBytes(StandardCharsets.UTF_8);
        Inspector inspector = new Value.StringValue(utf8);
        var sink = new Utf8CaptureSink();
        inspector.emit(sink);
        assertArrayEquals(utf8, sink.lastUtf8);
    }

}
