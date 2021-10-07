// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.UrlReference;
import com.yahoo.test.FunctionTestConfig;
import com.yahoo.test.FunctionTestConfig.*;
import com.yahoo.test.SimpletypesConfig;
import com.yahoo.config.codegen.*;
import com.yahoo.text.StringUtilities;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class InstanceResolverTest {

    @Test
    public void testApplyDefToBuilder() throws Exception {
        FunctionTestConfig.Builder builder = createVariableAccessBuilder();
        InnerCNode targetDef = getDef(FunctionTestConfig.CONFIG_DEF_SCHEMA);

        // Mutate the def, user has set different schema ...
        ((LeafCNode) targetDef.getChild("stringwithdef")).setDefaultValue(new DefaultValue("newDef", new DefLine.Type("string")));
        ((LeafCNode) targetDef.getChild("string_val")).setDefaultValue(new DefaultValue("newDefVal", new DefLine.Type("string")));
        ((LeafCNode) targetDef.getChild("enumwithdef")).setDefaultValue(new DefaultValue(Enumwithdef.FOOBAR2.toString(), new DefLine.Type("enum")));
        ((LeafCNode) targetDef.getChild("enum_val")).setDefaultValue(new DefaultValue(Enum_val.FOO.toString(), new DefLine.Type("enum")));
        ((LeafCNode) targetDef.getChild("basicStruct").getChild("foo")).setDefaultValue(new DefaultValue("basicSchmasic", new DefLine.Type("string")));
        ((LeafCNode) targetDef.getChild("basicStruct").getChild("bar")).setDefaultValue(new DefaultValue("89", new DefLine.Type("int")));
        InnerCNode rootStruct = ((InnerCNode) targetDef.getChild("rootStruct"));
        InnerCNode innerArr = (InnerCNode) rootStruct.getChild("innerArr");
        ((LeafCNode) innerArr.getChild("boolVal")).setDefaultValue(new DefaultValue("true", new DefLine.Type("bool")));
        ((LeafCNode) innerArr.getChild("stringVal")).setDefaultValue(new DefaultValue("derp", new DefLine.Type("string")));
        InnerCNode myArray = ((InnerCNode) targetDef.getChild("myarray"));
        myArray.children().put("intval", LeafCNode.newInstance(new DefLine.Type("int"), myArray, "intval", "-123424"));
        targetDef.children().put("myarray", myArray);
        InstanceResolver.applyDef(builder, targetDef);
        FunctionTestConfig c = new FunctionTestConfig(builder);
        assertEquals(c.string_val(), "foo");
        assertEquals(c.stringwithdef(), "newDef");
        assertEquals(c.enumwithdef(), Enumwithdef.FOOBAR2);
        assertEquals(c.enum_val(), Enum_val.FOOBAR);
        assertEquals(c.double_with_def(), -12, 0.0001);
        assertEquals(c.basicStruct().foo(), "basicSchmasic");
        assertEquals(c.basicStruct().bar(), 3);
        assertTrue(c.rootStruct().innerArr(0).boolVal());
        assertEquals(c.rootStruct().innerArr(0).stringVal(), "deep");
        assertEquals(c.myarray(0).intval(), -123424);
        assertEquals(c.urlVal().toString(), "url");
        assertEquals(c.urlArr(0).toString(), "url");
        assertEquals(c.myarray(0).urlVal().toString(), "url1");
        assertEquals(c.myarray(1).urlVal().toString(), "url2");
    }

    /**
     * Values unset on builder, trying to set them from def file, but type mismatches there
     */
    @Test
    public void testApplyDefToBuilderMismatches() throws Exception {
        FunctionTestConfig.Builder builder = createVariableAccessBuilderManyUnset();
        InnerCNode targetDef = getDef(FunctionTestConfig.CONFIG_DEF_SCHEMA);

        // Break the defs for these, they are unset on builder:
        targetDef.children().put("stringwithdef", LeafCNode.newInstance(new DefLine.Type("int"), targetDef, "stringwithdef", "1"));
        targetDef.children().put("int_val", LeafCNode.newInstance(new DefLine.Type("string"), targetDef, "int_val", "fooOO"));

        InstanceResolver.applyDef(builder, targetDef);
        try {
            FunctionTestConfig c = new FunctionTestConfig(builder);
            fail("No exception on incomplete builder");
        } catch (Exception e) {
        }
    }

    // copied from FunctionTest
    private FunctionTestConfig.Builder createVariableAccessBuilder() {
        return new FunctionTestConfig.Builder().
                bool_val(false).
                bool_with_def(true).
                int_val(5).
                int_with_def(-14).
                long_val(12345678901L).
                long_with_def(-9876543210L).
                double_val(41.23).
                double_with_def(-12).
                string_val("foo").
                //stringwithdef("bar").
                enum_val(Enum_val.FOOBAR).
                //enumwithdef(Enumwithdef.BAR2).
                refval(":parent:").
                refwithdef(":parent:").
                fileVal("etc").
                urlVal(new UrlReference("url")).
                boolarr(false).
                longarr(9223372036854775807L).
                longarr(-9223372036854775808L).
                doublearr(2344.0).
                doublearr(123.0).
                stringarr("bar").
                enumarr(Enumarr.VALUES).
                refarr(Arrays.asList(":parent:", ":parent", "parent:")).  // test collection based setter
                fileArr("bin").
                urlArr(new UrlReference("url")).

                basicStruct(new BasicStruct.Builder().
                        //foo("basicFoo").
                        bar(3).
                        intArr(310)).

                rootStruct(new RootStruct.Builder().
                        inner0(new RootStruct.Inner0.Builder().
                                index(11)).
                        inner1(new RootStruct.Inner1.Builder().
                                index(12)).
                        innerArr(new RootStruct.InnerArr.Builder().
                                //boolVal(true).
                                stringVal("deep"))).

                myarray(new Myarray.Builder().
                        //intval(-5).
                        stringval("baah").
                        stringval("yikes").
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file0").
                        urlVal(new UrlReference("url1")).
                        anotherarray(new Myarray.Anotherarray.Builder().
                                foo(7)).
                        myStruct(new Myarray.MyStruct.Builder().
                                a(1).
                                b(2))).

                myarray(new Myarray.Builder().
                        intval(5).
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file1").
                        urlVal(new UrlReference("url2")).
                        anotherarray(new Myarray.Anotherarray.Builder().
                                foo(1).
                                foo(2)).
                        myStruct(new Myarray.MyStruct.Builder().
                                a(-1).
                                b(-2)));

    }

    private FunctionTestConfig.Builder createVariableAccessBuilderManyUnset() {
        return new FunctionTestConfig.Builder().
                bool_val(false).
                bool_with_def(true).
                //int_val(5).
                int_with_def(-14).
                long_val(12345678901L).
                long_with_def(-9876543210L).
                double_val(41.23).
                double_with_def(-12).
                string_val("foo").
                //stringwithdef("bar").
                enum_val(Enum_val.FOOBAR).
                //enumwithdef(Enumwithdef.BAR2).
                refval(":parent:").
                refwithdef(":parent:").
                fileVal("etc").
                boolarr(false).
                longarr(9223372036854775807L).
                longarr(-9223372036854775808L).
                doublearr(2344.0).
                doublearr(123.0).
                stringarr("bar").
                enumarr(Enumarr.VALUES).
                refarr(Arrays.asList(":parent:", ":parent", "parent:")).  // test collection based setter
                fileArr("bin").

                basicStruct(new BasicStruct.Builder().
                        //foo("basicFoo").
                        bar(3).
                        intArr(310)).

                rootStruct(new RootStruct.Builder().
                        inner0(new RootStruct.Inner0.Builder().
                                index(11)).
                        inner1(new RootStruct.Inner1.Builder().
                                index(12)).
                        innerArr(new RootStruct.InnerArr.Builder().
                                //boolVal(true).
                                stringVal("deep"))).

                myarray(new Myarray.Builder().
                        intval(-5).
                        stringval("baah").
                        stringval("yikes").
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file0").
                        anotherarray(new Myarray.Anotherarray.Builder().
                                foo(7)).
                        myStruct(new Myarray.MyStruct.Builder().
                                a(1).
                                b(2))).

                myarray(new Myarray.Builder().
                        intval(5).
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file1").
                        anotherarray(new Myarray.Anotherarray.Builder().
                                foo(1).
                                foo(2)).
                        myStruct(new Myarray.MyStruct.Builder().
                                a(-1).
                                b(-2)));

    }

    private InnerCNode getDef(String[] schema) {
        ArrayList<String> def = new ArrayList<>();
        def.addAll(Arrays.asList(schema));
        return new DefParser("documentmanager",
                new StringReader(StringUtilities.implode(def.toArray(new String[def.size()]), "\n"))).getTree();
    }

    @Test
    public void testExtraFieldsAreIgnored() throws Exception {
        try {
            SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
            InnerCNode defWithExtra = new DefParser(SimpletypesConfig.CONFIG_DEF_NAME, new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n") + "\nnewfield string default=\"foo\"\n")).getTree();
            InstanceResolver.applyDef(builder, defWithExtra);
        } catch (NoSuchFieldException e) {
            fail("Should not fail on extra field");
        }
    }

}
