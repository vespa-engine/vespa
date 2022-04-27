// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.foo.AppConfig;
import com.yahoo.foo.ArraytypesConfig;
import com.yahoo.foo.IntConfig;
import com.yahoo.foo.MaptypesConfig;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.StructtypesConfig;
import com.yahoo.foo.UrlConfig;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.text.StringUtilities;
import org.junit.Test;

import java.io.StringReader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ConfigPayloadTest {

    @Test
    public void test_simple_builder() {
        SimpletypesConfig config = createSimpletypesConfig("stringval", "abcde");
        assertThat(config.stringval(), is("abcde"));
    }

    @Test
    public void require_that_arrays_are_built() {
        AppConfig config = createAppConfig("foo", "4", new String[] { "bar", "baz", "bim" });
        assertThat(config.message(), is("foo"));
        assertThat(config.times(), is(4));
        assertThat(config.a(0).name(), is("bar"));
        assertThat(config.a(1).name(), is("baz"));
        assertThat(config.a(2).name(), is("bim"));
    }

    @Test
    public void test_int_leaf_legal() {
        SimpletypesConfig config = createSimpletypesConfig("intval", "0");
        assertThat(config.intval(), is(0));
        config = createSimpletypesConfig("intval", String.valueOf(Integer.MIN_VALUE));
        assertThat(config.intval(), is(Integer.MIN_VALUE));
        config = createSimpletypesConfig("intval", String.valueOf(Integer.MAX_VALUE));
        assertThat(config.intval(), is(Integer.MAX_VALUE));
        config = createSimpletypesConfig("intval", String.valueOf(10));
        assertThat(config.intval(), is(10));
        config = createSimpletypesConfig("intval", String.valueOf(-10));
        assertThat(config.intval(), is(-10));
    }

    @Test (expected = RuntimeException.class)
    public void test_int_leaf_too_large() {
        createSimpletypesConfig("intval", Integer.MAX_VALUE + "00");
    }

    @Test (expected = RuntimeException.class)
    public void test_int_leaf_too_large_neg() {
        createSimpletypesConfig("intval", Integer.MIN_VALUE + "00");
    }

    @Test(expected=RuntimeException.class)
    public void test_int_leaf_illegal_string() {
        createSimpletypesConfig("intval", "illegal");
    }

    @Test(expected=RuntimeException.class)
    public void test_int_leaf_illegal_string_suffix() {
        createSimpletypesConfig("intval", "123illegal");
    }

    @Test(expected=RuntimeException.class)
    public void test_int_leaf_illegal_string_prefix() {
        createSimpletypesConfig("intval", "illegal123");
    }

    @Test
    public void test_that_empty_is_empty() {
        ConfigPayload payload = ConfigPayload.empty();
        assertTrue(payload.isEmpty());
        payload = ConfigPayload.fromString("{\"foo\":4}");
        assertFalse(payload.isEmpty());
    }


    @Test
    public void test_long_leaf() {
        SimpletypesConfig config = createSimpletypesConfig("longval", "0");
        assertThat(config.longval(), is(0L));
        config = createSimpletypesConfig("longval", String.valueOf(Long.MIN_VALUE));
        assertThat(config.longval(), is(Long.MIN_VALUE));
        config = createSimpletypesConfig("longval", String.valueOf(Long.MAX_VALUE));
        assertThat(config.longval(), is(Long.MAX_VALUE));
        config = createSimpletypesConfig("longval", String.valueOf(10));
        assertThat(config.longval(), is(10L));
        config = createSimpletypesConfig("longval", String.valueOf(-10));
        assertThat(config.longval(), is(- 10L));
    }

    @Test(expected = RuntimeException.class)
    public void test_long_leaf_illegal_string() {
        createSimpletypesConfig("longval", "illegal");
    }

    @Test (expected = RuntimeException.class)
    public void test_long_leaf_too_large() {
        createSimpletypesConfig("longval", Long.MAX_VALUE + "00");
    }

    @Test (expected = RuntimeException.class)
    public void test_long_leaf_too_large_neg() {
        createSimpletypesConfig("longval", Long.MIN_VALUE + "00");
    }

    @Test
    public void test_double_leaf() {
        SimpletypesConfig config = createSimpletypesConfig("doubleval", "0");
        assertEquals(0.0, config.doubleval(), 0.01);
        assertEquals(133.3, createSimpletypesConfig("doubleval", "133.3").doubleval(), 0.001);
        config = createSimpletypesConfig("doubleval", String.valueOf(Double.MIN_VALUE));
        assertEquals(Double.MIN_VALUE, config.doubleval(), 0.0000001);
        config = createSimpletypesConfig("doubleval", String.valueOf(Double.MAX_VALUE));
        assertEquals(Double.MAX_VALUE, config.doubleval(), 0.0000001);
    }

    @Test
    public void test_serializer() {
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        assertThat(payload.toString(true), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}"));
    }

    @Test
    public void test_serialize_url_fields() {
        ConfigPayload payload = ConfigPayload.fromInstance(new UrlConfig(new UrlConfig.Builder()));
        assertThat(payload.toString(true), is("{\"urlVal\":\"http://vespa.ai\"}"));
    }

    @Test(expected=RuntimeException.class)
    public void test_double_leaf_illegal_string() {
        createSimpletypesConfig("doubleval", "illegal");
    }

    @Test
    public void test_double_leaf_negative_infinity() {
        assertThat(createSimpletypesConfig("doubleval", "-Infinity").doubleval(), is(Double.NEGATIVE_INFINITY));
        assertThat(createSimpletypesConfig("doubleval", "Infinity").doubleval(), is(Double.POSITIVE_INFINITY));
    }

    @Test
    public void test_enum_leaf() {
        assertThat(createSimpletypesConfig("enumval", "VAL1").enumval(), is(SimpletypesConfig.Enumval.Enum.VAL1));
        assertThat(createSimpletypesConfig("enumval", "VAL2").enumval(), is(SimpletypesConfig.Enumval.Enum.VAL2));
    }

    @Test(expected=RuntimeException.class)
    public void test_enum_leaf_illegal_string() {
        createSimpletypesConfig("enumval", "ILLEGAL");
    }

    @Test
    public void test_bool_leaf() {
        SimpletypesConfig config = createSimpletypesConfig("boolval", "true");
        assertThat(config.boolval(), is(true));
        config = createSimpletypesConfig("boolval", "false");
        assertThat(config.boolval(), is(false));
        config = createSimpletypesConfig("boolval", "TRUE");
        assertThat(config.boolval(), is(true));
        config = createSimpletypesConfig("boolval", "FALSE");
        assertThat(config.boolval(), is(false));
    }

    @Test// FIXME: (expected = RuntimeException.class)
    public void test_bool_leaf_illegal() {
        createSimpletypesConfig("boolval", "illegal");
    }

    @Test
    public void test_string_illegal_value() {
        // TODO: What do we consider illegal string values?
        createSimpletypesConfig("stringval", "insert_illegal_value_please");
    }

    @Test
    public void test_int_array() {
        // Normal behavior
        ArraytypesConfig config = createArraytypesConfig("intarr", new String[] { "2", "3", "1", "-2", "5"});
        assertThat(config.intarr().size(),  is(5));
        assertThat(config.intarr(0), is(2));
        assertThat(config.intarr(1), is(3));
        assertThat(config.intarr(2), is(1));
        assertThat(config.intarr(3), is(-2));
        assertThat(config.intarr(4), is(5));

        final int size = 100;
        String [] largeArray = new String[size];
        for (int i = 0; i < size; i++) {
            int value = (int)(Math.random() * Integer.MAX_VALUE);
            largeArray[i] = String.valueOf(value);
        }
        config = createArraytypesConfig("intarr", largeArray);
        assertThat(config.intarr().size(), is(largeArray.length));
        for (int i = 0; i < size; i++) {
            assertThat(config.intarr(i), is(Integer.valueOf(largeArray[i])));
        }
    }

    @Test(expected = RuntimeException.class)
    public void test_int_array_illegal() {
        createArraytypesConfig("intarr", new String[] { "2", "3", "illegal", "-2", "5"});
    }

    @Test
    public void test_long_array() {
        // Normal behavior
        ArraytypesConfig config = createArraytypesConfig("longarr", new String[] { "2", "3", "1", "-2", "5"});
        assertThat(config.longarr().size(),  is(5));
        assertThat(config.longarr(0), is(2L));
        assertThat(config.longarr(1), is(3L));
        assertThat(config.longarr(2), is(1L));
        assertThat(config.longarr(3), is(- 2L));
        assertThat(config.longarr(4), is(5L));

        final int size = 100;
        String [] largeArray = new String[size];
        for (int i = 0; i < size; i++) {
            long value = (long) (Math.random() * Long.MAX_VALUE);
            largeArray[i] = String.valueOf(value);
        }
        config = createArraytypesConfig("longarr", largeArray);
        assertThat(config.longarr().size(), is(largeArray.length));
        for (int i = 0; i < size; i++) {
            assertThat(config.longarr(i), is(Long.valueOf(largeArray[i])));
        }
    }

    @Test
    public void test_double_array() {
        // Normal behavior
        ArraytypesConfig config = createArraytypesConfig("doublearr", new String[] { "2.1", "3.3", "1.5", "-2.1", "Infinity"});
        assertThat(config.doublearr().size(),  is(5));
        assertEquals(2.1, config.doublearr(0), 0.01);
        assertEquals(3.3, config.doublearr(1), 0.01);
        assertEquals(1.5, config.doublearr(2), 0.01);
        assertEquals(-2.1, config.doublearr(3), 0.01);
        assertEquals(Double.POSITIVE_INFINITY, config.doublearr(4), 0.01);
    }

    @Test
    public void test_enum_array() {
        // Normal behavior
        ArraytypesConfig config = createArraytypesConfig("enumarr", new String[] { "VAL1", "VAL2", "VAL1" });
        assertThat(config.enumarr().size(),  is(3));
        assertThat(config.enumarr(0), is(ArraytypesConfig.Enumarr.Enum.VAL1));
        assertThat(config.enumarr(1), is(ArraytypesConfig.Enumarr.Enum.VAL2));
        assertThat(config.enumarr(2), is(ArraytypesConfig.Enumarr.Enum.VAL1));
    }

    @Test
    public void test_simple_struct() {
        Slime slime = new Slime();
        addStructFields(slime.setObject().setObject("simple"), "foobar", "MALE", new String[] { "foo@bar", "bar@foo" });
        StructtypesConfig config = new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");

        assertThat(config.simple().name(), is("foobar"));
        assertThat(config.simple().gender(), is(StructtypesConfig.Simple.Gender.Enum.MALE));
        assertThat(config.simple().emails(0), is("foo@bar"));
        assertThat(config.simple().emails(1), is("bar@foo"));
    }

    @Test
    public void test_simple_struct_arrays() {
        StructtypesConfig config = createStructtypesConfigArray(new String[] { "foo", "bar" },
                                                                new String[] { "MALE", "FEMALE" });
        assertThat(config.simplearr(0).name(), is("foo"));
        assertThat(config.simplearr(0).gender(), is(StructtypesConfig.Simplearr.Gender.MALE));
        assertThat(config.simplearr(1).name(), is("bar"));
        assertThat(config.simplearr(1).gender(), is(StructtypesConfig.Simplearr.Gender.FEMALE));
    }


    @Test
    public void test_nested_struct() {
        StructtypesConfig config = createStructtypesConfigNested("foo", "FEMALE");
        assertThat(config.nested().inner().name(), is("foo"));
        assertThat(config.nested().inner().gender(), is(StructtypesConfig.Nested.Inner.Gender.Enum.FEMALE));
    }



    @Test
    public void test_nested_struct_array() {
        String [] names = { "foo" ,"bar" };
        String [] genders = { "FEMALE", "MALE" };
        String [][] emails = {
                { "foo@bar" , "bar@foo" },
                { "bim@bam", "bam@bim" }
        };
        StructtypesConfig config = createStructtypesConfigNestedArray(names, genders, emails);
        assertThat(config.nestedarr(0).inner().name(), is("foo"));
        assertThat(config.nestedarr(0).inner().gender(), is(StructtypesConfig.Nestedarr.Inner.Gender.FEMALE));
        assertThat(config.nestedarr(0).inner().emails(0), is("foo@bar"));
        assertThat(config.nestedarr(0).inner().emails(1), is("bar@foo"));

        assertThat(config.nestedarr(1).inner().name(), is("bar"));
        assertThat(config.nestedarr(1).inner().gender(), is(StructtypesConfig.Nestedarr.Inner.Gender.MALE));
        assertThat(config.nestedarr(1).inner().emails(0), is("bim@bam"));
        assertThat(config.nestedarr(1).inner().emails(1), is("bam@bim"));
    }


    @Test
    public void test_complex_struct_array() {
        String [][] names = {
                { "foo", "bar" },
                { "baz", "bim" }
        };
        String [][] genders = {
                { "FEMALE", "MALE" },
                { "MALE", "FEMALE" }
        };
        StructtypesConfig config = createStructtypesConfigComplexArray(names, genders);
        assertThat(config.complexarr(0).innerarr(0).name(), is("foo"));
        assertThat(config.complexarr(0).innerarr(0).gender(), is(StructtypesConfig.Complexarr.Innerarr.Gender.Enum.FEMALE));
        assertThat(config.complexarr(0).innerarr(1).name(), is("bar"));
        assertThat(config.complexarr(0).innerarr(1).gender(), is(StructtypesConfig.Complexarr.Innerarr.Gender.Enum.MALE));

        assertThat(config.complexarr(1).innerarr(0).name(), is("baz"));
        assertThat(config.complexarr(1).innerarr(0).gender(), is(StructtypesConfig.Complexarr.Innerarr.Gender.Enum.MALE));
        assertThat(config.complexarr(1).innerarr(1).name(), is("bim"));
        assertThat(config.complexarr(1).innerarr(1).gender(), is(StructtypesConfig.Complexarr.Innerarr.Gender.Enum.FEMALE));
    }

    @Test
    public void test_simple_map() {
        Slime slime = new Slime();
        Cursor map = slime.setObject().setObject("stringmap");
        map.setString("key","val");

        MaptypesConfig config = new ConfigPayload(slime).toInstance(MaptypesConfig.class, "");
        assertThat(config.stringmap("key"), is("val"));
    }

    @Test
    public void test_map_of_struct() {
        Slime slime = new Slime();
        Cursor map = slime.setObject().setObject("innermap");
        map.setObject("one").setLong("foo", 1);
        map.setObject("two").setLong("foo", 2);

        MaptypesConfig config = new ConfigPayload(slime).toInstance(MaptypesConfig.class, "");
        assertThat(config.innermap("one").foo(), is(1));
        assertThat(config.innermap("two").foo(), is(2));
    }

    @Test
    public void test_map_of_map() {
        Slime slime = new Slime();
        Cursor map = slime.setObject().setObject("nestedmap").setObject("my-nested").setObject("inner");
        map.setLong("one", 1);
        map.setLong("two", 2);

        MaptypesConfig config = new ConfigPayload(slime).toInstance(MaptypesConfig.class, "");
        assertThat(config.nestedmap("my-nested").inner("one"), is(1));
        assertThat(config.nestedmap("my-nested").inner("two"), is(2));
    }

    /* Non existent fields of all types must be ignored to allow adding new fields to a config definition
     * without breaking hosted Vespa applications that have config overrides for the given config class.
     * The generated payload for the user override will contain the new field (empty as the user doesn't
     * override it), because it exists in the latest config def version. The config class version follows
     * the applications's Vespa version, which may be older and doesn't have the new struct. Hence, we just
     * ignore unknown fields in the payload.
     */

    @Test
    public void non_existent_leaf_in_payload_is_ignored() {
        SimpletypesConfig config = createSimpletypesConfig("non_existent", "");
        assertNotNull(config);
    }

    @Test
    public void non_existent_struct_in_payload_is_ignored() {
        Slime slime = new Slime();
        addStructFields(slime.setObject().setObject("non_existent"), "", "", null);
        StructtypesConfig config = new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
        assertNotNull(config);
    }

    @Test
    public void non_existent_struct_in_struct_in_payload_is_ignored() {
        Slime slime = new Slime();
        addStructFields(slime.setObject().setObject("nested").setObject("non_existent_inner"), "", "", null);
        StructtypesConfig config = new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
        assertNotNull(config);
    }

    @Test
    public void non_existent_array_of_struct_in_payload_is_ignored() {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("non_existent_arr");
        array.addObject().setString("name", "val");
        StructtypesConfig config = new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
        assertNotNull(config);
    }

    @Test
    public void non_existent_struct_in_array_of_struct_in_payload_is_ignored() {
        Slime slime = new Slime();
        Cursor nestedArrEntry = slime.setObject().setArray("nestedarr").addObject();
        addStructFields(nestedArrEntry.setObject("inner"), "existing", "MALE", null);
        addStructFields(nestedArrEntry.setObject("non_existent"), "non-existent", "MALE", null);

        StructtypesConfig config =  new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
        assertThat(config.nestedarr(0).inner().name(), is("existing"));
    }

    @Test
    public void non_existent_simple_map_in_payload_is_ignored() {
        Slime slime = new Slime();
        Cursor map = slime.setObject().setObject("non_existent_map");
        map.setString("key","val");
        MaptypesConfig config = new ConfigPayload(slime).toInstance(MaptypesConfig.class, "");
        assertNotNull(config);
    }

    @Test
    public void non_existent_map_of_struct_in_payload_is_ignored() {
        Slime slime = new Slime();
        Cursor map = slime.setObject().setObject("non_existent_inner_map");
        map.setObject("one").setLong("foo", 1);
        MaptypesConfig config = new ConfigPayload(slime).toInstance(MaptypesConfig.class, "");
        assertNotNull(config);
    }

    @Test
    public void non_existent_map_in_map_in_payload_is_ignored() {
        Slime slime = new Slime();
        Cursor map = slime.setObject().setObject("nestedmap").setObject("my-nested");
        map.setObject("inner").setLong("one", 1);
        map.setObject("non_existent").setLong("non-existent", 0);

        MaptypesConfig config = new ConfigPayload(slime).toInstance(MaptypesConfig.class, "");
        assertThat(config.nestedmap("my-nested").inner("one"), is(1));
    }

    @Test
    public void test_function_test() {
        // TODO: Test function test config as a complete config example
    }

    @Test
    public void test_escaped_string() {
        SimpletypesConfig config = createSimpletypesConfig("stringval", "b=\"escaped\"");
        assertThat(config.stringval(), is("b=\"escaped\""));
    }

    @Test
    public void test_unicode() {
        SimpletypesConfig config = createSimpletypesConfig("stringval", "Hei \u00E6\u00F8\u00E5 \uBC14\uB451 \u00C6\u00D8\u00C5 hallo");
        assertThat(config.stringval(), is("Hei \u00E6\u00F8\u00E5 \uBC14\uB451 \u00C6\u00D8\u00C5 hallo"));
    }

    @Test
    public void test_empty_payload() {
        Slime slime = new Slime();
        slime.setObject();
        IntConfig config = new ConfigPayload(slime).toInstance(IntConfig.class, "");
        assertThat(config.intVal(), is(1));
    }

    @Test
    public void test_applying_extra_default_values() {
        InnerCNode clientDef = new DefParser(SimpletypesConfig.CONFIG_DEF_NAME, new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n") + "\nnewfield int default=3\n")).getTree();
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        payload = payload.applyDefaultsFromDef(clientDef);
        assertThat(payload.toString(true), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\",\"newfield\":\"3\"}"));
    }

    /*
     * TODO: Test invalid slime trees?
     * TODO: Test sending in wrong class
     */

    /**********************************************************************************************
     * Helper methods. consider moving out to another class for reuse by merge tester.            *
     **********************************************************************************************/
    private AppConfig createAppConfig(String message, String times, String [] names) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("message", message);
        root.setString("times", times);
        Cursor arr = root.setArray("a");
        for (String name : names) {
            Cursor obj = arr.addObject();
            obj.setString("name", name);
        }
        return new ConfigPayload(slime).toInstance(AppConfig.class, "");
    }

    private SimpletypesConfig createSimpletypesConfig(String field, String value) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(field, value);
        return new ConfigPayload(slime).toInstance(SimpletypesConfig.class, "");
    }

    private ArraytypesConfig createArraytypesConfig(String field, String [] values) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor array = root.setArray(field);
        for (String value : values) {
            array.addString(value);
        }
        return new ConfigPayload(slime).toInstance(ArraytypesConfig.class, "");
    }


    private void addStructFields(Cursor struct, String name, String gender, String [] emails) {
        struct.setString("name", name);
        struct.setString("gender", gender);
        if (emails != null) {
            Cursor array = struct.setArray("emails");
            for (String email : emails) {
                array.addString(email);
            }
        }
    }

    private StructtypesConfig createStructtypesConfigArray(String[] names, String[] genders) {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("simplearr");
        assertEquals(names.length, genders.length);
        for (int i = 0; i < names.length; i++) {
            addStructFields(array.addObject(), names[i], genders[i], null);
        }
        return new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
    }

    private StructtypesConfig createStructtypesConfigNested(String name, String gender) {
        Slime slime = new Slime();
        addStructFields(slime.setObject().setObject("nested").setObject("inner"), name, gender, null);
        return new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
    }

    private StructtypesConfig createStructtypesConfigNestedArray(String[] names, String [] genders, String [][] emails) {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("nestedarr");
        assertEquals(names.length, genders.length);
        for (int i = 0; i < names.length; i++) {
            addStructFields(array.addObject().setObject("inner"), names[i], genders[i], emails[i]);
        }
        return new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
    }

    private StructtypesConfig createStructtypesConfigComplexArray(String [][] names, String [][] genders) {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("complexarr");
        assertEquals(names.length, genders.length);
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i].length, genders[i].length);

            Cursor innerarr = array.addObject().setArray("innerarr");
            for (int k = 0; k < names[i].length; k++) {
                addStructFields(innerarr.addObject(), names[i][k], genders[i][k], null);
            }
        }
        return new ConfigPayload(slime).toInstance(StructtypesConfig.class, "");
    }
}
