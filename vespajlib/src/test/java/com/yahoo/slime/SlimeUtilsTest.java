// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.text.Utf8;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class SlimeUtilsTest {

    @Test
    public void test_copying_slime_types_into_cursor() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "foobie");
        Cursor subobj = root.setObject("bar");

        Slime slime2 = new Slime();
        Cursor root2 = slime2.setObject();
        root2.setString("a", "a");
        root2.setLong("b", 2);
        root2.setBool("c", true);
        root2.setDouble("d", 3.14);
        root2.setData("e", new byte[]{0x64});
        root2.setNix("f");

        SlimeUtils.copyObject(slime2.get(), subobj);

        assertEquals("{\"foo\":\"foobie\",\"bar\":{\"a\":\"a\",\"b\":2,\"c\":true,\"d\":3.14,\"e\":\"0x64\",\"f\":null}}",
                     root.toString());
    }

    @Test
    public void test_copying_slime_arrays_into_cursor() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "foobie");
        Cursor subobj = root.setObject("bar");

        Slime slime2 = new Slime();
        Cursor root2 = slime2.setObject();
        Cursor array = root2.setArray("a");
        array.addString("foo");
        array.addLong(4);
        array.addBool(true);
        array.addDouble(3.14);
        array.addNix();
        array.addData(new byte[]{0x64});
        Cursor objinner = array.addObject();
        objinner.setString("inner", "binner");

        SlimeUtils.copyObject(slime2.get(), subobj);

        assertEquals("{\"foo\":\"foobie\",\"bar\":{\"a\":[\"foo\",4,true,3.14,null,\"0x64\",{\"inner\":\"binner\"}]}}",
                     root.toString());
    }

    @Test
    public void test_slime_to_json() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "foobie");
        root.setObject("bar");
        String json = Utf8.toString(SlimeUtils.toJsonBytes(slime));
        assertEquals("{\"foo\":\"foobie\",\"bar\":{}}", json);
    }

    @Test
    public void test_json_to_slime() {
        byte[] json = Utf8.toBytes("{\"foo\":\"foobie\",\"bar\":{}}");
        Slime slime = SlimeUtils.jsonToSlime(json);
        assertEquals("foobie", slime.get().field("foo").asString());
        assertTrue(slime.get().field("bar").valid());
    }

    @Test
    public void test_json_to_slime_or_throw() {
        Slime slime = SlimeUtils.jsonToSlimeOrThrow("{\"foo\":\"foobie\",\"bar\":{}}");
        assertEquals("foobie", slime.get().field("foo").asString());
        assertTrue(slime.get().field("bar").valid());
    }

    @Test
    public void test_invalid_json() {
        try {
            SlimeUtils.jsonToSlimeOrThrow("foo");
            fail();
        } catch (RuntimeException e) {
            assertEquals("Unexpected character 'o'", e.getMessage());
        }
    }

    @Test
    public void test_stream() {
        String json = "{\"constant\":0,\"list\":[1,2,4,3,0],\"object\":{\"a\":1,\"c\":3,\"b\":2}}";
        Inspector inspector = SlimeUtils.jsonToSlimeOrThrow(json).get();
        assertEquals(0, SlimeUtils.entriesStream(inspector.field("constant")).count());
        assertEquals(0, SlimeUtils.entriesStream(inspector.field("object")).count());

        assertEquals(List.of(1L, 2L, 4L, 3L, 0L),
                     SlimeUtils.entriesStream(inspector.field("list")).map(Inspector::asLong).collect(Collectors.toList()));
    }

}
