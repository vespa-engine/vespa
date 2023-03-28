// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.text.Utf8;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
                     SlimeUtils.entriesStream(inspector.field("list")).map(Inspector::asLong).toList());
    }

    @Test
    public void verifyObjectEquality() {
        Slime slimeLeft = new Slime();
        Cursor left = slimeLeft.setObject();
        left.setString("a", "A");
        left.setString("b", "B");

        Slime slimeRight = new Slime();
        Cursor right = slimeRight.setObject();
        right.setString("b", "B");
        right.setString("a", "A");

        assertTrue(left.equalTo(right));
        assertTrue(right.equalTo(left));
        assertTrue(left.equalTo(left));

        right.setString("c", "C");
        assertFalse(left.equalTo(right));
        assertFalse(right.equalTo(left));
    }

    @Test
    public void verifyArrayEquality() {
        Slime slimeLeft = new Slime();
        Cursor left = slimeLeft.setArray();
        left.addArray().addString("a");
        left.addArray().addString("b");

        Slime slimeRight = new Slime();
        Cursor right = slimeRight.setArray();
        right.addArray().addString("a");
        right.addArray().addString("b");

        assertTrue(left.equalTo(right));
        assertTrue(right.equalTo(left));
        assertTrue(left.equalTo(left));

        right.addArray().addString("c");
        assertFalse(left.equalTo(right));
        assertFalse(right.equalTo(left));

        // Order matters
        Slime slimeRight2 = new Slime();
        Cursor right2 = slimeRight2.setObject();
        right2.addArray().addString("b");
        right2.addArray().addString("a");
        assertFalse(left.equalTo(right2));
        assertFalse(right2.equalTo(left));
    }

    @Test
    public void verifyPrimitiveEquality() {
        Slime left = new Slime();
        Cursor leftObject = left.setObject();
        populateWithPrimitives(leftObject, true);

        Slime right = new Slime();
        Cursor rightObject = right.setObject();
        populateWithPrimitives(rightObject, true);

        assertEqualTo(left.get().field("bool"), right.get().field("bool"));
        assertEqualTo(left.get().field("nix"), right.get().field("nix"));
        assertEqualTo(left.get().field("long"), right.get().field("long"));
        assertEqualTo(left.get().field("string"), right.get().field("string"));
        assertEqualTo(left.get().field("data"), right.get().field("data"));
        assertEqualTo(left.get(), right.get());

        assertNotEqualTo(left.get().field("bool"), right.get().field("nix"));
        assertNotEqualTo(left.get().field("nix"), right.get().field("string"));
        assertNotEqualTo(left.get().field("string"), right.get().field("data"));
        assertNotEqualTo(left.get().field("bool"), right.get().field("data"));
        assertNotEqualTo(left.get().field("bool"), right.get().field("long"));
    }

    @Test
    public void verifyPrimitiveNotEquality() {
        Slime left = new Slime();
        Cursor leftObject = left.setObject();
        populateWithPrimitives(leftObject, true);

        Slime right = new Slime();
        Cursor rightObject = right.setObject();
        populateWithPrimitives(rightObject, false);

        assertNotEqualTo(left.get().field("bool"), right.get().field("bool"));
        assertEqualTo(left.get().field("nix"), right.get().field("nix"));
        assertNotEqualTo(left.get().field("long"), right.get().field("long"));
        assertNotEqualTo(left.get().field("string"), right.get().field("string"));
        assertNotEqualTo(left.get().field("data"), right.get().field("data"));
        assertNotEqualTo(left.get(), right.get());
    }

    @Test
    public void testNixEquality() {
        assertEqualTo(NixValue.invalid(), NixValue.invalid());
        assertEqualTo(NixValue.instance(), NixValue.instance());
        assertNotEqualTo(NixValue.instance(), NixValue.invalid());
        assertNotEqualTo(NixValue.invalid(), NixValue.instance());
    }

    private void populateWithPrimitives(Cursor cursor, boolean enabled) {
        cursor.setBool("bool", enabled ? true : false);
        cursor.setNix("nix");
        cursor.setLong("long", enabled ? 1 : 0);
        cursor.setString("string", enabled ? "enabled" : "disabled");
        cursor.setDouble("double", enabled ? 1.5 : 0.5);
        cursor.setData("data", (enabled ? "edata" : "ddata").getBytes(StandardCharsets.UTF_8));
    }

    private void assertEqualTo(Inspector left, Inspector right) {
        assertTrue("'" + left + "' is not equal to '" + right + "'", left.equalTo(right));
    }

    private void assertNotEqualTo(Inspector left, Inspector right) {
        assertTrue("'" + left + "' is equal to '" + right + "'", !left.equalTo(right));
    }
}
