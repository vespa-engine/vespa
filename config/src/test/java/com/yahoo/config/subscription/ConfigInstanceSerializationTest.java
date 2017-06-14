// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.config.codegen.DefLine;
import com.yahoo.vespa.config.ConfigPayload;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @author vegardh
 */
public class ConfigInstanceSerializationTest {

    private DefLine.Type stringType = new DefLine.Type("string");
    private DefLine.Type intType = new DefLine.Type("int");
    private DefLine.Type longType = new DefLine.Type("long");
    private DefLine.Type boolType = new DefLine.Type("bool");
    private DefLine.Type doubleType = new DefLine.Type("double");
    private DefLine.Type fileType = new DefLine.Type("file");
    private DefLine.Type refType = new DefLine.Type("reference");

    @Test
    public void require_symmetrical_serialization_and_deserialization_with_builder() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();

        // NOTE: configId must be ':parent:' because the library replaces ReferenceNodes with that value with
        //        the instance's configId. (And the config used here contains such nodes.)
        List<String> lines = ConfigInstance.serialize(config);
        ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(lines);

        FunctionTestConfig config2 = ConfigInstanceUtil.getNewInstance(FunctionTestConfig.class, ":parent:", payload);
        assertThat(config, is(config2));
        assertThat(ConfigInstance.serialize(config), is(ConfigInstance.serialize(config2)));
    }

/** Looks like everything in the commented block tests unused api's.. remove?

    @Test
    public void testSerializeAgainstConfigDefinitionAllowNothing() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();
        InnerCNode def = new InnerCNode("function-test");
        List<String> payload = Validator.serialize(config, def);
        Assert.assertEquals(payload.size(), 0);
    }

    @Test
    public void testSerializeAgainstConfigDefinitionAllLeaves() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();
        InnerCNode def = new InnerCNode("function-test");
        def.children().put("bool_val", LeafCNode.newInstance(boolType, def, "bool_val"));
        def.children().put("bool_with_def", LeafCNode.newInstance(boolType, def, "bool_with_def"));
        def.children().put("int_val", LeafCNode.newInstance(intType, def, "int_val"));
        def.children().put("int_with_def", LeafCNode.newInstance(intType, def, "int_with_def"));
        def.children().put("long_val", LeafCNode.newInstance(longType, def, "long_val"));
        def.children().put("long_with_def", LeafCNode.newInstance(longType, def, "long_with_def"));
        def.children().put("double_val", LeafCNode.newInstance(doubleType, def, "double_val"));
        def.children().put("double_with_def", LeafCNode.newInstance(doubleType, def, "double_with_def"));
        def.children().put("string_val", LeafCNode.newInstance(stringType, def, "string_val"));
        def.children().put("stringwithdef", LeafCNode.newInstance(stringType, def, "stringwithdef"));
        def.children().put("enum_val", LeafCNode.newInstance(enumType(new String[]{"FOO", "BAR", "FOOBAR"}), def, "enum_val"));
        def.children().put("enumwithdef", LeafCNode.newInstance(enumType(new String[]{"FOO2", "BAR2", "FOOBAR2"}), def, "enumwithdef", "BAR2"));
        def.children().put("refval", LeafCNode.newInstance(refType, def, "refval"));
        def.children().put("refwithdef", LeafCNode.newInstance(refType, def, "refwithdef"));
        def.children().put("fileVal", LeafCNode.newInstance(fileType, def, "fileVal"));

        List<String> payload = Validator.serialize(config, def);
        String plString = payload.toString();
        Assert.assertTrue(plString.matches(".*bool_val false.*"));
        Assert.assertTrue(plString.matches(".*bool_with_def true.*"));
        Assert.assertTrue(plString.matches(".*int_val 5.*"));
        Assert.assertTrue(plString.matches(".*int_with_def -14.*"));
        Assert.assertTrue(plString.matches(".*long_val 12345678901.*"));
        Assert.assertTrue(plString.matches(".*long_with_def -9876543210.*"));
        Assert.assertTrue(plString.matches(".*double_val 41\\.23.*"));
        Assert.assertTrue(plString.matches(".*double_with_def -12.*"));
        Assert.assertTrue(plString.matches(".*string_val \"foo\".*"));
        Assert.assertTrue(plString.matches(".*stringwithdef \"bar and foo\".*"));
        Assert.assertTrue(plString.matches(".*enum_val FOOBAR.*"));
        Assert.assertTrue(plString.matches(".*enumwithdef BAR2.*"));
        Assert.assertTrue(plString.matches(".*refval \\:parent\\:.*"));
        Assert.assertTrue(plString.matches(".*refwithdef \\:parent\\:.*"));
        Assert.assertTrue(plString.matches(".*fileVal \"etc\".*"));
    }

    @Test
    public void testSerializeAgainstConfigDefinitionSomeLeaves() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();
        InnerCNode def = new InnerCNode("function-test");
        def.children().put("stringwithdef", LeafCNode.newInstance(stringType, def, "stringwithdef"));
        def.children().put("long_with_def", LeafCNode.newInstance(longType, def, "long_with_def"));
        // But not double_with_def etc, and no structs/arrays
        List<String> payload = Validator.serialize(config, def);
        String plString = payload.toString();
        Assert.assertTrue(plString.matches(".*long_with_def \\-9876543210.*"));
        Assert.assertTrue(plString.matches(".*stringwithdef \"bar and foo\".*"));
        Assert.assertFalse(plString.matches(".*double_with_def.*"));
        Assert.assertFalse(plString.matches(".*fileVal \"etc\".*"));
        Assert.assertFalse(plString.matches(".*basicStruct.*"));
    }

    @Test
    public void testSerializationAgainstConfigDefinitionAddedValsInDef() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();
        InnerCNode def = new InnerCNode("function-test");
        def.children().put("stringwithdef", LeafCNode.newInstance(stringType, def, "stringwithdef"));
        def.children().put("someotherstring", LeafCNode.newInstance(stringType, def, "someotherstring", "some other"));
        def.children().put("long_with_def", LeafCNode.newInstance(longType, def, "long_with_def"));
        def.children().put("some_other_long", LeafCNode.newInstance(longType, def, "some_other_long", "88"));
        def.children().put("some_other_enum", LeafCNode.newInstance(enumType(new String[]{"hey", "ho", "lets", "go"}), def, "some_other_enum", "lets"));
        def.children().put("int_val_nofdef", LeafCNode.newInstance(intType, def, "int_val_nodef", null));

        // But not double_with_def etc, and no structs/arrays
        List<String> payload = Validator.serialize(config, def);
        String plString = payload.toString();
        Assert.assertTrue(plString.matches(".*long_with_def \\-9876543210.*"));
        Assert.assertTrue(plString.matches(".*stringwithdef \"bar and foo\".*"));
        Assert.assertTrue(plString.matches(".*.someotherstring \"some other\".*"));
        Assert.assertTrue(plString.matches(".*some_other_long 88.*"));
        Assert.assertTrue(plString.matches(".*some_other_enum lets.*"));
        Assert.assertFalse(plString.matches(".*double_with_def.*"));
        Assert.assertFalse(plString.matches(".*fileVal \"etc\".*"));
        Assert.assertFalse(plString.matches(".*basicStruct.*"));
        Assert.assertFalse(plString.matches(".*int_val_nodef.*"));
    }

    @Test
    public void testSerializeAgainstConfigDefinitionMismatchAllWays() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();

        // Create all sorts of mismatches in the def schema used to serialize
        InnerCNode def = new InnerCNode("function-test");
        def.children().put("long_with_def", LeafCNode.newInstance(longType, def, "long_with_def"));
        def.children().put("stringwithdef", LeafCNode.newInstance(intType, def, "stringwithdef"));
        def.children().put("basicStruct", LeafCNode.newInstance(intType, def, "basicStruct"));
        InnerCNode doubleValWrong = new InnerCNode("double_val");
        doubleValWrong.children().put("foo", LeafCNode.newInstance(intType, def, "foo"));
        def.children().put("double_val", doubleValWrong);
        InnerCNode myArray = new InnerCNode("myarray");
        myArray.children().put("intval", LeafCNode.newInstance(stringType, myArray, "foo"));
        InnerCNode myStruct = new InnerCNode("myStruct");
        myStruct.children().put("a", LeafCNode.newInstance(stringType, myStruct, "foo"));
        myArray.children().put("myStruct", myStruct);
        def.children().put("myarray", myArray);

        List<String> payload = Validator.serialize(config, def);
        String plString = payload.toString();
        Assert.assertTrue(plString.matches(".*long_with_def.*"));
        Assert.assertFalse(plString.matches(".*stringwithdef.*"));
        Assert.assertFalse(plString.matches(".*basicStruct.*"));
        Assert.assertFalse(plString.matches(".*double_val.*"));
        Assert.assertFalse(plString.matches(".*intval.*"));
        Assert.assertFalse(plString.matches(".*\\.a.*"));
    }

    @Test
    public void testSerializeAgainstConfigDefinitionComplex() {
        FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();

        // Build a pretty complex def programatically
        InnerCNode def = new InnerCNode("function-test");
        def.children().put("stringwithdef", LeafCNode.newInstance(stringType, def, "stringwithdef"));
        def.children().put("someUnknownStringNoDefault", LeafCNode.newInstance(stringType, def, "someUnknownStringNoDefault"));
        InnerCNode basicStruct = new InnerCNode("basicStruct");
        basicStruct.children().put("foo", LeafCNode.newInstance(stringType, def, "foo")); // but not bar
        InnerCNode rootStruct = new InnerCNode("rootStruct");
        InnerCNode inner1 = new InnerCNode("inner1");
        InnerCNode someUnknwonStruct = new InnerCNode("someUnknownStruct");
        InnerCNode someUnknownInner = new InnerCNode("someUnknownInner");
        InnerCNode innerArr = new InnerCNode("innerArr");
        rootStruct.children().put("inner1", inner1);
        rootStruct.children().put("someUnknownStruct", someUnknwonStruct);
        rootStruct.children().put("someUnknownInner", someUnknownInner);
        rootStruct.children().put("innerArr", innerArr);
        InnerCNode myarray = new InnerCNode("myarray");
        InnerCNode unknownInner = new InnerCNode("unknownInner");
        def.children().put("basicStruct", basicStruct);
        def.children().put("rootStruct", rootStruct);
        def.children().put("myarray", myarray);
        def.children().put("unknownInner", unknownInner);
        inner1.children().put("index", LeafCNode.newInstance(intType, inner1, "index"));
        inner1.children().put("someUnknownInt", LeafCNode.newInstance(intType, inner1, "someUnknownInt", "-98"));
        inner1.children().put("someUnknownIntNoDefault", LeafCNode.newInstance(intType, inner1, "someUnknownIntNoDefault"));
        inner1.children().put("someUnknownEnum", LeafCNode.newInstance(enumType(new String[]{"goo", "go", "gorilla"}), inner1, "someUnknownEnum", "go"));
        inner1.children().put("someUnknownEnumNoDefault", LeafCNode.newInstance(enumType(new String[]{"foo", "bar", "baz"}), inner1, "someUnknownEnumNoDefault"));
        someUnknwonStruct.children().put("anint", LeafCNode.newInstance(intType, someUnknwonStruct, "anint", "3"));// But no instances of this in config
        someUnknownInner.children().put("along", LeafCNode.newInstance(longType, someUnknownInner, "along", "234"));// No instance in config
        innerArr.children().put("boolVal", LeafCNode.newInstance(boolType, innerArr, "boolVal"));
        innerArr.children().put("someUnknownDouble", LeafCNode.newInstance(doubleType, innerArr, "someUnknownDouble", "-675.789"));
        innerArr.children().put("someUnknownDoubleNoDefault", LeafCNode.newInstance(doubleType, innerArr, "someUnknownDoubleNoDefault"));
        myarray.children().put("fileVal", LeafCNode.newInstance(fileType, myarray, "fileVal"));
        myarray.children().put("stringval", new InnerCNode("stringval[]"));
        // TODO make sure default for file is not allowed
        //myarray.children().put("someUnknownFile", LeafCNode.newInstance(fileType, myarray, "someUnknownFile", "opt/"));
        unknownInner.children().put("aDouble", LeafCNode.newInstance(doubleType, unknownInner, "aDouble", "1234"));
        def.children().put("longarr", new InnerCNode("longarr[]"));
        def.children().put("boolarr", new InnerCNode("boolarr[]"));
        def.children().put("doublearr", new InnerCNode("doublearr[]"));
        def.children().put("stringarr", new InnerCNode("stringarr[]"));
        def.children().put("fileArr", new InnerCNode("fileArr[]"));
        def.children().put("refarr", new InnerCNode("refarr[]"));
        def.children().put("enumarr", new InnerCNode("enumarr[]"));
        List<String> payload = Validator.serialize(config, def);
        String plString = payload.toString();
        Assert.assertFalse(plString.matches(".*long_with_def \\-9876543210.*"));
        Assert.assertFalse(plString.matches(".*someUnknownStringNoDefault.*"));
        Assert.assertTrue(plString.matches(".*stringwithdef \"bar and foo\".*"));
        Assert.assertFalse(plString.matches(".*double_with_def.*"));
        Assert.assertFalse(plString.matches(".*fileVal etc.*"));
        Assert.assertTrue(plString.matches(".*basicStruct\\.foo \"basicFoo\".*"));
        Assert.assertFalse(plString.matches(".*basicStruct\\.bar.*"));
        Assert.assertFalse(plString.matches(".*rootStruct\\.inner0.*"));
        Assert.assertFalse(plString.matches(".*unknownInner.*"));
        Assert.assertFalse(plString.matches(".*rootStruct\\.someUnknownStruct.*"));
        Assert.assertFalse(plString.matches(".*rootStruct\\.someUnknownInner.*"));
        Assert.assertFalse(plString.matches(".*rootStruct\\.inner1\\.name.*"));
        Assert.assertTrue(plString.matches(".*rootStruct\\.inner1\\.index 12.*"));
        Assert.assertTrue(plString.matches(".*rootStruct\\.inner1\\.someUnknownInt -98.*"));
        Assert.assertTrue(plString.matches(".*rootStruct\\.inner1\\.someUnknownEnum go.*"));
        Assert.assertTrue(plString.matches(".*rootStruct\\.innerArr\\[0\\]\\.boolVal true.*"));
        Assert.assertFalse(plString.matches(".*someUnknownEnumNoDefault.*"));
        Assert.assertFalse(plString.matches(".*someUnknownDoubleNoDefault.*"));
        Assert.assertFalse(plString.matches(".*someUnknownIntNoDefault.*"));
        Assert.assertTrue(plString.matches(".*rootStruct\\.innerArr\\[0\\]\\.someUnknownDouble -675.789.*"));
        Assert.assertFalse(plString.matches(".*rootStruct\\.innerArr\\[0\\]\\.stringVal*"));
        Assert.assertFalse(plString.matches(".*myarray\\[0\\].intval.*"));
        Assert.assertTrue(plString.matches(".*myarray\\[0\\].fileVal \"file0\".*"));
        //assertTrue(plString.matches(".*myarray\\[0\\].someUnknownFile \"opt/\".*"));
        Assert.assertTrue(plString.matches(".*myarray\\[0\\].stringval\\[0\\] \"baah\".*"));
        Assert.assertTrue(plString.matches(".*myarray\\[0\\].stringval\\[1\\] \"yikes\".*"));
        Assert.assertTrue(plString.matches(".*myarray\\[1\\].fileVal \"file1\".*"));
        Assert.assertFalse(plString.matches(".*myarray\\[1\\].enumVal.*"));
        Assert.assertFalse(plString.matches(".*myarray\\[1\\].refVal.*"));
        Assert.assertTrue(plString.matches(".*boolarr\\[0\\] false.*"));
        Assert.assertTrue(plString.matches(".*longarr\\[0\\] 9223372036854775807.*"));
        Assert.assertTrue(plString.matches(".*longarr\\[1\\] -9223372036854775808.*"));
        Assert.assertTrue(plString.matches(".*doublearr\\[0\\] 2344\\.0.*"));
        Assert.assertTrue(plString.matches(".*doublearr\\[1\\] 123\\.0.*"));
        Assert.assertTrue(plString.matches(".*stringarr\\[0\\] \"bar\".*"));
        Assert.assertTrue(plString.matches(".*enumarr\\[0\\] VALUES.*"));
        Assert.assertTrue(plString.matches(".*refarr\\[0\\] \\:parent\\:.*"));
        Assert.assertTrue(plString.matches(".*refarr\\[1\\] \\:parent.*"));
        Assert.assertTrue(plString.matches(".*refarr\\[2\\] parent\\:.*"));
        Assert.assertTrue(plString.matches(".*fileArr\\[0\\] \"bin\".*"));
    }

    private DefLine.Type enumType(String[] strings) {
        return new DefLine.Type("enum").setEnumArray(strings);
    }
**/
}
