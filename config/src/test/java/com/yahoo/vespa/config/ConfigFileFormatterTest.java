// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.foo.ArraytypesConfig;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.StructtypesConfig;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.foo.MaptypesConfig;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.text.StringUtilities;
import com.yahoo.text.Utf8;
import org.junit.Test;
import org.junit.Ignore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ConfigFileFormatterTest {

    private final String expected_simpletypes = "stringval \"foo\"\n" +
            "intval 324234\n"  +
            "longval 324\n"  +
            "doubleval 3.455\n" +
            "enumval VAL2\n" +
            "boolval true\n";

    @Test
    public void require_that_basic_formatting_is_correct() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("stringval", "foo");
        root.setString("intval", "324234");
        root.setString("longval", "324");
        root.setString("doubleval", "3.455");
        root.setString("enumval", "VAL2");
        root.setString("boolval", "true");

        assertConfigFormat(slime, expected_simpletypes);
    }

    @Test
    public void require_that_basic_formatting_is_correct_with_types() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("stringval", "foo");
        root.setLong("intval", 324234);
        root.setLong("longval", 324);
        root.setDouble("doubleval", 3.455);
        root.setString("enumval", "VAL2");
        root.setBool("boolval", true);

        assertConfigFormat(slime, expected_simpletypes);
    }

    private void assertConfigFormat(Slime slime, String expected_simpletypes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals(expected_simpletypes, baos.toString());
    }

    @Test
    public void require_that_field_not_found_is_ignored() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("nosuchfield", "bar");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ConfigFileFormat(def).encode(baos, slime);
        assertTrue(baos.toString().isEmpty());
    }

    // TODO: Reenable this when we can reenable typechecking.
    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void require_that_illegal_int_throws_exception() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("intval", "invalid");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(new ByteArrayOutputStream(), slime);
    }

    // TODO: Reenable this when we can reenable typechecking.
    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void require_that_illegal_long_throws_exception() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("longval", "invalid");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(new ByteArrayOutputStream(), slime);
    }

    // TODO: Reenable this when we can reenable typechecking.
    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void require_that_illegal_double_throws_exception() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("doubleval", "invalid");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(new ByteArrayOutputStream(), slime);
    }

    @Test
    public void require_that_illegal_boolean_becomes_false() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("boolval", "invalid");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals("boolval false\n", baos.toString());
    }

    // TODO: Remove this when we can reenable typechecking.
    @Test
    public void require_that_types_are_not_checked() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("enumval", "null");
        root.setString("intval", "null");
        root.setString("longval", "null");
        root.setString("boolval", "null");
        root.setString("doubleval", "null");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals("enumval null\nintval null\nlongval null\nboolval false\ndoubleval null\n",
                baos.toString(StandardCharsets.UTF_8));
    }

    // TODO: Reenable this when we can reenable typechecking.
    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void require_that_illegal_enum_throws_exception() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("enumval", "invalid");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(new ByteArrayOutputStream(), slime);
    }

    @Test
    public void require_that_strings_are_encoded() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        String value = "\u7d22";
        root.setString("stringval", value);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals("stringval \"" + value + "\"\n", baos.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void require_that_array_formatting_is_correct() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor boolarr = root.setArray("boolarr");
        boolarr.addString("true");
        boolarr.addString("false");
        Cursor doublearr = root.setArray("doublearr");
        doublearr.addString("3.14");
        doublearr.addString("1.414");
        Cursor enumarr = root.setArray("enumarr");
        enumarr.addString("VAL1");
        enumarr.addString("VAL2");
        Cursor intarr = root.setArray("intarr");
        intarr.addString("3");
        intarr.addString("5");
        Cursor longarr = root.setArray("longarr");
        longarr.addString("55");
        longarr.addString("66");
        Cursor stringarr = root.setArray("stringarr");
        stringarr.addString("foo");
        stringarr.addString("bar");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InnerCNode def = new DefParser("arraytypes", new StringReader(StringUtilities.implode(ArraytypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals(
                "boolarr[0] true\n" +
                "boolarr[1] false\n" +
                "doublearr[0] 3.14\n" +
                "doublearr[1] 1.414\n" +
                "enumarr[0] VAL1\n" +
                "enumarr[1] VAL2\n" +
                "intarr[0] 3\n" +
                "intarr[1] 5\n" +
                "longarr[0] 55\n" +
                "longarr[1] 66\n" +
                "stringarr[0] \"foo\"\n" +
                "stringarr[1] \"bar\"\n",
                baos.toString());
    }

    @Test
    public void require_that_map_formatting_is_correct() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor boolval = root.setObject("boolmap");
        boolval.setString("foo", "true");
        boolval.setString("bar", "false");
        root.setObject("intmap").setString("foo", "1234");
        root.setObject("longmap").setString("foo", "12345");
        root.setObject("doublemap").setString("foo", "3.14");
        root.setObject("stringmap").setString("foo", "bar");
        root.setObject("innermap").setObject("bar").setString("foo", "1234");
        root.setObject("nestedmap").setObject("baz").setObject("inner").setString("foo", "1234");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InnerCNode def = new DefParser("maptypes", new StringReader(StringUtilities.implode(MaptypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals(
                "boolmap{\"foo\"} true\n" +
                "boolmap{\"bar\"} false\n" +
                "intmap{\"foo\"} 1234\n" +
                "longmap{\"foo\"} 12345\n" +
                "doublemap{\"foo\"} 3.14\n" +
                "stringmap{\"foo\"} \"bar\"\n" +
                "innermap{\"bar\"}.foo 1234\n" +
                "nestedmap{\"baz\"}.inner{\"foo\"} 1234\n",
                baos.toString());
    }

    @Test
    public void require_that_struct_formatting_is_correct() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor simple = root.setObject("simple");
        simple.setString("name", "myname");
        simple.setString("gender", "FEMALE");
        Cursor array = simple.setArray("emails");
        array.addString("foo@bar.com");
        array.addString("bar@baz.net");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InnerCNode def = new DefParser("structtypes", new StringReader(StringUtilities.implode(StructtypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals(
                "simple.name \"myname\"\n" +
                "simple.gender FEMALE\n" +
                "simple.emails[0] \"foo@bar.com\"\n" +
                "simple.emails[1] \"bar@baz.net\"\n",
                baos.toString());
    }

    @Test
    public void require_that_complex_struct_formatting_is_correct() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        Cursor nested = root.setObject("nested");
        Cursor nested_inner = nested.setObject("inner");
        nested_inner.setString("name", "baz");
        nested_inner.setString("gender", "FEMALE");
        Cursor nested_inner_arr = nested_inner.setArray("emails");
        nested_inner_arr.addString("foo");
        nested_inner_arr.addString("bar");

        Cursor nestedarr = root.setArray("nestedarr");
        Cursor nestedarr1 = nestedarr.addObject();
        Cursor inner1 = nestedarr1.setObject("inner");
        inner1.setString("name", "foo");
        inner1.setString("gender", "FEMALE");
        Cursor inner1arr = inner1.setArray("emails");
        inner1arr.addString("foo@bar");
        inner1arr.addString("bar@foo");

        Cursor complexarr = root.setArray("complexarr");
        Cursor complexarr1 = complexarr.addObject();
        Cursor innerarr1 = complexarr1.setArray("innerarr");
        Cursor innerarr11 = innerarr1.addObject();
        innerarr11.setString("name", "bar");
        innerarr11.setString("gender", "MALE");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InnerCNode def = new DefParser("structtypes", new StringReader(StringUtilities.implode(StructtypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals("nested.inner.name \"baz\"\n" +
                        "nested.inner.gender FEMALE\n" +
                        "nested.inner.emails[0] \"foo\"\n" +
                        "nested.inner.emails[1] \"bar\"\n" +
                        "nestedarr[0].inner.name \"foo\"\n" +
                        "nestedarr[0].inner.gender FEMALE\n" +
                        "nestedarr[0].inner.emails[0] \"foo@bar\"\n" +
                        "nestedarr[0].inner.emails[1] \"bar@foo\"\n" +
                        "complexarr[0].innerarr[0].name \"bar\"\n" +
                        "complexarr[0].innerarr[0].gender MALE\n",
                baos.toString());
    }

    @Test
    public void require_that_strings_are_properly_escaped() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("stringval", "some\"quotes\\\"instring");
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ConfigFileFormat(def).encode(baos, slime);
        assertEquals("stringval \"some\\\"quotes\\\\\\\"instring\"\n", baos.toString());
    }

    @Test
    @Ignore
    public void require_that_utf8_works() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        final String input = "Hei \u00E6\u00F8\u00E5 \n \uBC14\uB451 \u00C6\u00D8\u00C5 hallo";
        root.setString("stringval", input);
        System.out.println(bytesToHexString(Utf8.toBytes(input)));
        InnerCNode def = new DefParser("simpletypes", new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n"))).getTree();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ConfigFileFormat(def).encode(baos, slime);
        System.out.println(bytesToHexString(baos.toByteArray()));
        assertEquals(Utf8.toString(baos.toByteArray()), "stringval \"" + input + "\"\n");
    }

    private static String bytesToHexString(byte[] bytes){
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes){
            sb.append(String.format("%02x", b&0xff));
        }
        return sb.toString();
    }
}
