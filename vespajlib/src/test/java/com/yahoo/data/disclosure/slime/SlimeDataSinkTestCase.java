// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure.slime;

import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author johsol
 */
public class SlimeDataSinkTestCase {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                    () -> "Expected " + SlimeUtils.toJson(expected) +
                            " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testValuesInObject() {
        var sink = new SlimeDataSink();
        sink.startObject();

        sink.fieldName("int");
        sink.intValue(1024);

        sink.fieldName("bool");
        sink.booleanValue(true);

        sink.fieldName("empty");
        sink.emptyValue();

        sink.fieldName("double");
        sink.doubleValue(3.5);

        sink.fieldName("string");
        sink.stringValue("hello");

        byte[] bytes = new byte[]{1, 2, 3};
        sink.fieldName("data");
        sink.dataValue(bytes);

        sink.endObject();

        var expected = new Slime();
        var obj = expected.setObject();
        obj.setLong("int", 1024);
        obj.setBool("bool", true);
        obj.setNix("empty");
        obj.setDouble("double", 3.5);
        obj.setString("string", "hello");
        obj.setData("data", bytes);

        assertSlime(expected, sink.getSlime());
    }

    @Test
    public void testValuesInArray() {
        var sink = new SlimeDataSink();
        sink.startArray();
        // [1, true, nix, 2.5, "foo", byte[9,8]]
        sink.longValue(1L);
        sink.booleanValue(true);
        sink.emptyValue();
        sink.doubleValue(2.5);
        sink.stringValue("foo");
        byte[] bytes = new byte[]{9, 8};
        sink.dataValue(bytes);
        sink.endArray();

        var expected = new Slime();
        var arr = expected.setArray();
        arr.addLong(1L);
        arr.addBool(true);
        arr.addNix();
        arr.addDouble(2.5);
        arr.addString("foo");
        arr.addData(bytes);

        assertSlime(expected, sink.getSlime());
    }

    @Test
    public void testNestedObjectAndArray() {
        var sink = new SlimeDataSink();
        sink.startObject();

        // nums: [1, 2]
        sink.fieldName("nums");
        sink.startArray();
        sink.longValue(1L);
        sink.longValue(2L);
        sink.endArray();

        // meta: { ok: true }
        sink.fieldName("meta");
        sink.startObject();
        sink.fieldName("ok");
        sink.booleanValue(true);
        sink.endObject();

        sink.endObject();

        var expected = new Slime();
        var root = expected.setObject();
        var nums = root.setArray("nums");
        nums.addLong(1L);
        nums.addLong(2L);
        var meta = root.setObject("meta");
        meta.setBool("ok", true);

        assertSlime(expected, sink.getSlime());
    }

    @Test
    public void testArrayOfObjects() {
        var sink = new SlimeDataSink();

        // [{ "id": 1 }, { "id2", 2 }]
        sink.startArray();

        sink.startObject();
        sink.fieldName("id");
        sink.longValue(1L);
        sink.endObject();

        sink.startObject();
        sink.fieldName("id");
        sink.longValue(2L);
        sink.endObject();

        sink.endArray();

        var expected = new Slime();
        var arr = expected.setArray();
        var o1 = arr.addObject();
        o1.setLong("id", 1L);
        var o2 = arr.addObject();
        o2.setLong("id", 2L);

        assertSlime(expected, sink.getSlime());
    }

    @Test
    public void testHandlesFieldNameUtf8AndUtf16() {
        var sink = new SlimeDataSink();

        // { "utf8_name": 1, "utf16_name": 2 }
        sink.startObject();
        sink.fieldName("utf8_name".getBytes(StandardCharsets.UTF_8));
        sink.intValue(1);
        sink.fieldName("utf16_name".getBytes(StandardCharsets.UTF_8));
        sink.intValue(2);
        sink.endObject();

        var expected = new Slime();
        var obj = expected.setObject();
        obj.setLong("utf8_name", 1);
        obj.setLong("utf16_name", 2);

        assertSlime(expected, sink.getSlime());
    }

    @Test
    public void testNumericDefaultDelegates() {
        var sink = new SlimeDataSink();
        sink.startArray();
        sink.intValue(10);
        sink.shortValue((short) 20);
        sink.byteValue((byte) 30);
        sink.floatValue(1.5f);
        sink.endArray();

        var expected = new Slime();
        var arr = expected.setArray();
        arr.addLong(10);
        arr.addLong(20);
        arr.addLong(30);
        arr.addDouble(1.5f); // floatValue delegates to doubleValue

        assertSlime(expected, sink.getSlime());
    }

    @Test
    public void testLeafValues() {
        {
            var sink = new SlimeDataSink();
            sink.intValue(1);
            var expected = SlimeUtils.jsonToSlime("1");
            assertSlime(expected, sink.getSlime());
        }

        {
            var sink = new SlimeDataSink();
            sink.booleanValue(true);
            var expected = SlimeUtils.jsonToSlime("true");
            assertSlime(expected, sink.getSlime());
        }

        {
            var sink = new SlimeDataSink();
            sink.doubleValue(3.14);
            var expected = SlimeUtils.jsonToSlime("3.14");
            assertSlime(expected, sink.getSlime());
        }

        {
            var sink = new SlimeDataSink();
            sink.stringValue("some_string");
            var expected = SlimeUtils.jsonToSlime("\"some_string\"");
            assertSlime(expected, sink.getSlime());
        }
    }
}
