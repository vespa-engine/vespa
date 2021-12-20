// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import com.yahoo.vespa.config.ConfigDefinition.EnumDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for ConfigDefinition.
 *
 * @author hmusum
 */
public class ConfigDefinitionTest {

    @Test
    public void testVersionComparator() {
        Comparator<String> c = new ConfigDefinition.VersionComparator();

        assertEquals(0, c.compare("1", "1"));
        assertEquals(0, c.compare("1-0", "1"));
        assertEquals(0, c.compare("1-0-0", "1"));
        assertEquals(0, c.compare("1-0-0", "1-0"));
        assertEquals(0, c.compare("0-1-1", "0-1-1"));
        assertEquals(0, c.compare("0-1-0", "0-1"));
        assertEquals(-1, c.compare("0", "1"));
        assertEquals(-1, c.compare("0-1-0", "0-1-1"));
        assertEquals(-1, c.compare("0-1-0", "1-1-1"));
        assertEquals(-1, c.compare("0-0-1", "0-1"));
        assertEquals(1, c.compare("0-1-1", "0-1-0"));
        assertEquals(1, c.compare("1-1-1", "0-1-0"));
        assertEquals(1, c.compare("0-1", "0-0-1"));
        assertEquals(1, c.compare("1-1", "1"));

        List<String> versions = Arrays.asList("25", "5", "1-1", "0-2-3", "1", "1-0");
        Collections.sort(versions, new ConfigDefinition.VersionComparator());
        List<String> solution = Arrays.asList("0-2-3", "1", "1-0", "1-1", "5", "25");
        assertEquals(solution, versions);
    }

    @Test
    public void testIntDefaultValues() {
        ConfigDefinition def = new ConfigDefinition("foo", "namespace1");

        def.addIntDef("foo");
        def.addIntDef("bar", 0);
        def.addIntDef("baz", 1);
        def.addIntDef("xyzzy", 2, 0, null);

        assertNull(def.getIntDefs().get("foo").getDefVal());
        assertEquals(ConfigDefinition.INT_MIN, def.getIntDefs().get("foo").getMin());
        assertEquals(ConfigDefinition.INT_MAX, def.getIntDefs().get("foo").getMax());
        assertEquals(0, def.getIntDefs().get("bar").getDefVal().longValue());
        assertEquals(1, def.getIntDefs().get("baz").getDefVal().longValue());

        assertEquals(2, def.getIntDefs().get("xyzzy").getDefVal().longValue());
        assertEquals(0, def.getIntDefs().get("xyzzy").getMin().longValue());
        assertEquals(ConfigDefinition.INT_MAX, def.getIntDefs().get("xyzzy").getMax());
    }

    @Test
    public void testLongDefaultValues() {
        ConfigDefinition def = new ConfigDefinition("foo", "namespace1");

        def.addLongDef("foo");
        def.addLongDef("bar", 1234567890123L);
        def.addLongDef("xyzzy", 2L, 0L, null);

        assertNull(def.getLongDefs().get("foo").getDefVal());
        assertEquals(ConfigDefinition.LONG_MIN, def.getLongDefs().get("foo").getMin());
        assertEquals(ConfigDefinition.LONG_MAX, def.getLongDefs().get("foo").getMax());
        assertEquals(1234567890123L, def.getLongDefs().get("bar").getDefVal().longValue());

        assertEquals(2L, def.getLongDefs().get("xyzzy").getDefVal().longValue());
        assertEquals(0L, def.getLongDefs().get("xyzzy").getMin().longValue());
        assertEquals(ConfigDefinition.LONG_MAX, def.getLongDefs().get("xyzzy").getMax());
    }

    @Test
    public void testDefaultsPayloadMap() {
        ConfigDefinition def = new ConfigDefinition("foo", "namespace1");
        def.addStringDef("mystring");
        def.addStringDef("mystringdef", "foo");
        def.addBoolDef("mybool");
        def.addBoolDef("mybooldef", true);
        def.addIntDef("myint");
        def.addIntDef("myintdef", 1);
        def.addLongDef("mylong");
        def.addLongDef("mylongdef", 11L);
        def.addDoubleDef("mydouble");
        def.addDoubleDef("mydoubledef", 2d);
        EnumDef ed = new EnumDef(new ArrayList<>() {{
            add("a1");
            add("a2");
        }}, null);
        EnumDef eddef = new EnumDef(new ArrayList<>() {{
            add("a11");
            add("a22");
        }}, "a22");
        def.addEnumDef("myenum", ed);
        def.addEnumDef("myenumdef", eddef);
        def.addReferenceDef("myref");
        def.addReferenceDef("myrefdef", "reff");
        def.addFileDef("myfile");
        def.addFileDef("myfiledef", "etc");
    }

    @Test
    public void testVerification() {
        ConfigDefinition def = new ConfigDefinition("foo", "bar");
        def.addBoolDef("boolval");
        def.addStringDef("stringval");
        def.addIntDef("intval");
        def.addLongDef("longval");
        def.addDoubleDef("doubleval");
        def.addEnumDef("enumval", new EnumDef(List.of("FOO"), "FOO"));
        def.addReferenceDef("refval");
        def.addFileDef("fileval");
        def.addInnerArrayDef("innerarr");
        def.addLeafMapDef("leafmap");
        ConfigDefinition.ArrayDef intArray = def.arrayDef("intArray");
        intArray.setTypeSpec(new ConfigDefinition.TypeSpec("intArray", "int", null, null, Integer.MIN_VALUE, Integer.MAX_VALUE));

        ConfigDefinition.ArrayDef longArray = def.arrayDef("longArray");
        longArray.setTypeSpec(new ConfigDefinition.TypeSpec("longArray", "long", null, null, Long.MIN_VALUE, Long.MAX_VALUE));

        ConfigDefinition.ArrayDef doubleArray = def.arrayDef("doubleArray");
        doubleArray.setTypeSpec(new ConfigDefinition.TypeSpec("doubleArray", "double", null, null, Double.MIN_VALUE, Double.MAX_VALUE));

        ConfigDefinition.ArrayDef enumArray = def.arrayDef("enumArray");
        enumArray.setTypeSpec(new ConfigDefinition.TypeSpec("enumArray", "enum", null, "VALID", null, null));

        ConfigDefinition.ArrayDef stringArray = def.arrayDef("stringArray");
        stringArray.setTypeSpec(new ConfigDefinition.TypeSpec("stringArray", "string", null, null, null, null));

        def.structDef("struct");

        assertVerify(def, "boolval", "true");
        assertVerify(def, "boolval", "false");
        assertVerify(def, "boolval", "invalid", IllegalArgumentException.class);


        assertVerify(def, "stringval", "foobar");
        assertVerify(def, "stringval", "foobar");
        assertVerify(def, "intval", "123");
        assertVerify(def, "intval", "foobar", IllegalArgumentException.class);
        assertVerify(def, "longval", "1234");
        assertVerify(def, "longval", "foobar", IllegalArgumentException.class);
        assertVerify(def, "doubleval", "foobar", IllegalArgumentException.class);
        assertVerify(def, "doubleval", "3");
        assertVerify(def, "doubleval", "3.14");
        assertVerify(def, "enumval", "foobar", IllegalArgumentException.class);
        assertVerify(def, "enumval", "foo", IllegalArgumentException.class);
        assertVerify(def, "enumval", "FOO");
        assertVerify(def, "refval", "foobar");
        assertVerify(def, "fileval", "foobar");

        assertVerifyComplex(def, "innerarr");
        assertVerifyComplex(def, "leafmap");
        assertVerifyComplex(def, "intArray");
        assertVerifyComplex(def, "longArray");
        assertVerifyComplex(def, "doubleArray");
        assertVerifyComplex(def, "enumArray");
        assertVerifyComplex(def, "stringArray");
        assertVerifyArray(intArray, "1345", 0);
        assertVerifyArray(intArray, "invalid", 0, IllegalArgumentException.class);
        assertVerifyArray(longArray, "1345", 0);
        assertVerifyArray(longArray, "invalid", 0, IllegalArgumentException.class);
        assertVerifyArray(doubleArray, "1345", 0);
        assertVerifyArray(doubleArray, "1345.3", 0);
        assertVerifyArray(doubleArray, "invalid", 0, IllegalArgumentException.class);
        assertVerifyArray(enumArray, "valid", 0, IllegalArgumentException.class);
        assertVerifyArray(enumArray, "VALID", 0);
        assertVerifyArray(enumArray, "inVALID", 0, IllegalArgumentException.class);
        assertVerifyArray(stringArray, "VALID", 0);
        assertVerifyComplex(def, "struct");
    }

    private void assertVerifyArray(ConfigDefinition.ArrayDef def, String val, int index) {
        def.verify(val, index);
    }

    private void assertVerifyArray(ConfigDefinition.ArrayDef def, String val, int index, Class<?> expectedException) {
        try {
            def.verify(val, index);
        } catch (Exception e) {
            if (!(e.getClass().isAssignableFrom(expectedException))) {
                throw e;
            }
        }
    }

    private void assertVerify(ConfigDefinition def, String id, String val) {
        def.verify(id, val);
    }

    private void assertVerify(ConfigDefinition def, String id, String val, Class<?> expectedException) {
        try {
            def.verify(id, val);
        } catch (Exception e) {
            if (!(e.getClass().isAssignableFrom(expectedException))) {
                throw e;
            }
        }
    }

    private void assertVerifyComplex(ConfigDefinition def, String id) {
        def.verify(id);
    }

}
