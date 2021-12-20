// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.DefParser;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ConfigDefinitionBuilder.
 *
 * @author hmusum
 */
public class ConfigDefinitionBuilderTest {

    private static final String TEST_DIR = "src/test/resources/configs/def-files";
    private static final String DEF_NAME = TEST_DIR + "/function-test.def";


    @Test
    // TODO Test ranges
    public void testCreateConfigDefinition() throws IOException {
        File defFile = new File(DEF_NAME);
        DefParser defParser = new DefParser(defFile.getName(), new FileReader(defFile));
        CNode root = defParser.getTree();

        ConfigDefinition def = ConfigDefinitionBuilder.createConfigDefinition(root);

        assertNotNull(def);
        assertThat(def.getBoolDefs().size(), is(2));
        assertNull(def.getBoolDefs().get("bool_val").getDefVal());
        assertThat(def.getBoolDefs().get("bool_with_def").getDefVal(), is(false));

        assertThat(def.getIntDefs().size(), is(2));
        assertNull(def.getIntDefs().get("int_val").getDefVal());
        assertThat(def.getIntDefs().get("int_with_def").getDefVal(), is(-545));

        assertThat(def.getLongDefs().size(), is(2));
        assertNull(def.getLongDefs().get("long_val").getDefVal());
        assertThat(def.getLongDefs().get("long_with_def").getDefVal(), is(-50000000000L));

        assertThat(def.getDoubleDefs().size(), is(2));
        assertNull(def.getDoubleDefs().get("double_val").getDefVal());
        assertThat(def.getDoubleDefs().get("double_with_def").getDefVal(), is(-6.43));

        assertThat(def.getEnumDefs().size(), is(3));
        assertTrue(def.getEnumDefs().containsKey("enum_val"));
        assertThat(def.getEnumDefs().get("enum_val").getVals().size(), is(3));
        assertThat(def.getEnumDefs().get("enum_val").getVals().get(0), is("FOO"));
        assertThat(def.getEnumDefs().get("enum_val").getVals().get(1), is("BAR"));
        assertThat(def.getEnumDefs().get("enum_val").getVals().get(2), is("FOOBAR"));

        assertTrue(def.getEnumDefs().containsKey("enumwithdef"));
        assertThat(def.getEnumDefs().get("enumwithdef").getDefVal(), is("BAR2"));

        assertTrue(def.getEnumDefs().containsKey("onechoice"));
        assertThat(def.getEnumDefs().get("onechoice").getDefVal(), is("ONLYFOO"));

        assertThat(def.getStringDefs().size(), is(2));
        assertNull(def.getStringDefs().get("string_val").getDefVal()); // The return value is a String, so null if no default value
        assertThat(def.getStringDefs().get("stringwithdef").getDefVal(), is("foobar"));

        assertThat(def.getReferenceDefs().size(), is(2));
        assertNotNull(def.getReferenceDefs().get("refval"));
        assertThat(def.getReferenceDefs().get("refwithdef").getDefVal(), is(":parent:"));

        assertThat(def.getFileDefs().size(), is(1));
        assertNotNull(def.getFileDefs().get("fileVal"));

        assertThat(def.getArrayDefs().size(), is(9));
        assertNotNull(def.getArrayDefs().get("boolarr"));
        assertThat(def.getArrayDefs().get("boolarr").getTypeSpec().getType(), is("bool"));

        assertNotNull(def.getArrayDefs().get("enumarr"));
        assertThat(def.getArrayDefs().get("enumarr").getTypeSpec().getType(), is("enum"));
        assertThat(def.getArrayDefs().get("enumarr").getTypeSpec().getEnumVals().toString(), is("[ARRAY, VALUES]"));

        assertNotNull(def.getArrayDefs().get("refarr"));
        assertThat(def.getArrayDefs().get("refarr").getTypeSpec().getType(), is("reference"));

        assertNotNull(def.getArrayDefs().get("fileArr"));
        assertThat(def.getArrayDefs().get("fileArr").getTypeSpec().getType(), is("file"));

        assertThat(def.getStructDefs().size(), is(2));
        assertNotNull(def.getStructDefs().get("basicStruct"));
        assertThat(def.getStructDefs().get("basicStruct").getStringDefs().size(), is(1));
        assertThat(def.getStructDefs().get("basicStruct").getStringDefs().get("foo").getDefVal(), is("basic"));
        assertThat(def.getStructDefs().get("basicStruct").getIntDefs().size(), is(1));
        assertNull(def.getStructDefs().get("basicStruct").getIntDefs().get("bar").getDefVal());
        assertThat(def.getStructDefs().get("basicStruct").getArrayDefs().size(), is(1));
        assertThat(def.getStructDefs().get("basicStruct").getArrayDefs().get("intArr").getTypeSpec().getType(), is("int"));

        assertNotNull(def.getStructDefs().get("rootStruct"));
        assertNotNull(def.getStructDefs().get("rootStruct").getStructDefs().get("inner0"));
        assertNotNull(def.getStructDefs().get("rootStruct").getStructDefs().get("inner1"));
        assertThat(def.getStructDefs().get("rootStruct").getInnerArrayDefs().size(), is(1));
        assertNotNull(def.getStructDefs().get("rootStruct").getInnerArrayDefs().get("innerArr"));
        assertThat(def.getStructDefs().get("rootStruct").getInnerArrayDefs().get("innerArr").getStringDefs().size(), is(1));

        assertThat(def.getInnerArrayDefs().size(), is(1));
        assertNotNull(def.getInnerArrayDefs().get("myarray"));
        assertThat(def.getInnerArrayDefs().get("myarray").getIntDefs().get("intval").getDefVal(), is(14));
        assertThat(def.getInnerArrayDefs().get("myarray").getArrayDefs().size(), is(1));
        assertNotNull(def.getInnerArrayDefs().get("myarray").getArrayDefs().get("stringval"));
        assertThat(def.getInnerArrayDefs().get("myarray").getArrayDefs().get("stringval").getTypeSpec().getType(), is("string"));
        assertThat(def.getInnerArrayDefs().get("myarray").getEnumDefs().get("enumval").getDefVal(), is("TYPE"));
        assertNull(def.getInnerArrayDefs().get("myarray").getReferenceDefs().get("refval").getDefVal());
        assertThat(def.getInnerArrayDefs().get("myarray").getInnerArrayDefs().size(), is(1));
        assertThat(def.getInnerArrayDefs().get("myarray").getInnerArrayDefs().get("anotherarray").getIntDefs().get("foo").getDefVal(), is(-4));
        assertNull(def.getInnerArrayDefs().get("myarray").getStructDefs().get("myStruct").getIntDefs().get("a").getDefVal());
        assertThat(def.getInnerArrayDefs().get("myarray").getStructDefs().get("myStruct").getIntDefs().get("b").getDefVal(), is(2));

        // Maps
        assertEquals(def.getLeafMapDefs().size(), 4);
        assertEquals(def.getLeafMapDefs().get("intMap").getTypeSpec().getType(), "int");
        assertEquals(def.getLeafMapDefs().get("stringMap").getTypeSpec().getType(), "string");
        assertEquals(def.getStructMapDefs().size(), 1);
        assertNull(def.getStructMapDefs().get("myStructMap").getIntDefs().get("myInt").getDefVal());
        assertNull(def.getStructMapDefs().get("myStructMap").getStringDefs().get("myString").getDefVal());
        assertEquals(def.getStructMapDefs().get("myStructMap").getIntDefs().get("myIntDef").getDefVal(), (Integer)56);
        assertEquals(def.getStructMapDefs().get("myStructMap").getStringDefs().get("myStringDef").getDefVal(), "g");

        // Ranges
    }

}
