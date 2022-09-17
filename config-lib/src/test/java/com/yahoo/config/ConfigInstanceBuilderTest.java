// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import com.yahoo.foo.MaptypesConfig;
import com.yahoo.foo.StructtypesConfig;
import com.yahoo.test.FunctionTestConfig;
import com.yahoo.test.IntConfig;
import com.yahoo.test.RestartConfig;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.foo.StructtypesConfig.Simple.Gender.Enum.FEMALE;
import static com.yahoo.test.FunctionTestConfig.BasicStruct;
import static com.yahoo.test.FunctionTestConfig.Enum_val;
import static com.yahoo.test.FunctionTestConfig.Enumarr;
import static com.yahoo.test.FunctionTestConfig.Enumwithdef;
import static com.yahoo.test.FunctionTestConfig.Myarray;
import static com.yahoo.test.FunctionTestConfig.RootStruct;
import static com.yahoo.test.FunctionTestConfig.MyStructMap;
import static com.yahoo.foo.MaptypesConfig.Innermap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class ConfigInstanceBuilderTest {

    @Test
    void struct_values_can_be_set_without_declaring_a_new_struct_builder() {
        var builder = new StructtypesConfig.Builder();
        builder.simple
                .name("myname")
                .gender(FEMALE);

        StructtypesConfig config = builder.build();
        assertEquals("myname", config.simple().name());
        assertEquals(FEMALE, config.simple().gender());
    }

    @Test
    void leaf_map_setter_merges_maps() {
        MaptypesConfig.Builder builder = new MaptypesConfig.Builder()
                .intmap("one", 1);

        Map<String, Integer> newMap = new HashMap<>();
        newMap.put("two", 2);
        builder.intmap(newMap);

        MaptypesConfig config = new MaptypesConfig(builder);
        assertEquals(1, config.intmap("one"));
        assertEquals(2, config.intmap("two"));
    }

    @Test
    void inner_map_setter_merges_maps() {
        MaptypesConfig.Builder builder = new MaptypesConfig.Builder()
                .innermap("one", new Innermap.Builder()
                        .foo(1));

        Map<String, Innermap.Builder> newMap = new HashMap<>();
        newMap.put("two", new Innermap.Builder().foo(2));
        builder.innermap(newMap);

        MaptypesConfig config = new MaptypesConfig(builder);
        assertEquals(1, config.innermap("one").foo());
        assertEquals(2, config.innermap("two").foo());
    }

    @Test
    void testVariableAccessWithBuilder() {
        FunctionTestConfig config = createVariableAccessConfigWithBuilder();
        assertVariableAccessValues(config, ":parent:");
    }

    @Test
    void require_that_unset_builder_fields_are_null() throws Exception {
        FunctionTestConfig.Builder builder = new FunctionTestConfig.Builder();
        assertNull(getMember(builder, "bool_val"));
        assertNull(getMember(builder, "bool_with_def"));
        assertNull(getMember(builder, "int_val"));
        assertNull(getMember(builder, "int_with_def"));
        assertNull(getMember(builder, "long_val"));
        assertNull(getMember(builder, "long_with_def"));
        assertNull(getMember(builder, "double_val"));
        assertNull(getMember(builder, "double_with_def"));
        assertNull(getMember(builder, "string_val"));
        assertNull(getMember(builder, "stringwithdef"));
        assertNull(getMember(builder, "enum_val"));
        assertNull(getMember(builder, "enumwithdef"));
        assertNull(getMember(builder, "refval"));
        assertNull(getMember(builder, "refwithdef"));
        assertNull(getMember(builder, "fileVal"));

        BasicStruct.Builder basicStructBuilder = new BasicStruct.Builder();
        assertNull(getMember(basicStructBuilder, "foo"));
        assertNull(getMember(basicStructBuilder, "bar"));
    }

    @Test
    void require_that_set_builder_fields_are_nonNull() throws Exception {
        FunctionTestConfig.Builder builder = createVariableAccessBuilder();
        assertNotNull(getMember(builder, "bool_val"));
        assertNotNull(getMember(builder, "bool_with_def"));
        assertNotNull(getMember(builder, "int_val"));
        assertNotNull(getMember(builder, "int_with_def"));
        assertNotNull(getMember(builder, "long_val"));
        assertNotNull(getMember(builder, "long_with_def"));
        assertNotNull(getMember(builder, "double_val"));
        assertNotNull(getMember(builder, "double_with_def"));
        assertNotNull(getMember(builder, "string_val"));
        assertNotNull(getMember(builder, "stringwithdef"));
        assertNotNull(getMember(builder, "enum_val"));
        assertNotNull(getMember(builder, "enumwithdef"));
        assertNotNull(getMember(builder, "refval"));
        assertNotNull(getMember(builder, "refwithdef"));
        assertNotNull(getMember(builder, "fileVal"));

        BasicStruct.Builder basicStructBuilder = (BasicStruct.Builder) getMember(builder, "basicStruct");
        assertNotNull(getMember(basicStructBuilder, "foo"));
        assertNotNull(getMember(basicStructBuilder, "bar"));
    }

    @Test
    void require_that_config_can_be_recreated_from_another_configs_builder() {
        FunctionTestConfig original = createVariableAccessConfigWithBuilder();
        FunctionTestConfig copy = new FunctionTestConfig(new FunctionTestConfig.Builder(original));
        assertVariableAccessValues(copy, ":parent:");
    }

    private Object getMember(Object builder, String memberName) throws Exception {
        Field field = builder.getClass().getDeclaredField(memberName);
        field.setAccessible(true);
        return field.get(builder);
    }

    static FunctionTestConfig createVariableAccessConfigWithBuilder() {
        return new FunctionTestConfig(createVariableAccessBuilder());
    }

    static FunctionTestConfig.Builder createVariableAccessBuilder() {
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
                stringwithdef("bar and foo").
                enum_val(Enum_val.FOOBAR).
                enumwithdef(Enumwithdef.BAR2).
                refval(":parent:").
                refwithdef(":parent:").
                fileVal("etc").
                pathVal(FileReference.mockFileReferenceForUnitTesting(new File("pom.xml"))).
                urlVal(new UrlReference("http://docs.vespa.ai")).
                modelVal(ModelReference.unresolved(FileReference.mockFileReferenceForUnitTesting(new File("pom.xml")))).
                boolarr(false).
                longarr(9223372036854775807L).
                longarr(-9223372036854775808L).
                doublearr(2344.0).
                doublearr(123.0).
                stringarr("bar").
                enumarr(Enumarr.VALUES).
                refarr(Arrays.asList(":parent:", ":parent", "parent:")).  // test collection based setter
                fileArr("bin").
                intMap("one", 1).
                intMap("two", 2).
                stringMap("one", "first").
                filemap("f1", "/var").
                filemap("f2", "/store").
                urlMap("u1", new UrlReference("http://docs.vespa.ai/1")).
                urlMap("u2", new UrlReference("http://docs.vespa.ai/2")).

                basicStruct(new BasicStruct.Builder().
                        foo("basicFoo").
                        bar(3).
                        intArr(310).intArr(311)).

                rootStruct(new RootStruct.Builder().
                        inner0(new RootStruct.Inner0.Builder().
                                index(11)).
                        inner1(new RootStruct.Inner1.Builder().
                                index(12)).
                        innerArr(new RootStruct.InnerArr.Builder().
                                boolVal(true).
                                stringVal("deep")).
                        innerArr(new RootStruct.InnerArr.Builder().
                                boolVal(false).
                                stringVal("blue a=\"escaped\""))).

                myarray(new Myarray.Builder().
                        intval(-5).
                        stringval("baah").
                        stringval("yikes").
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file0").
                        urlVal(new UrlReference("http://docs.vespa.ai/1")).
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
                        urlVal(new UrlReference("http://docs.vespa.ai/2")).
                        anotherarray(new Myarray.Anotherarray.Builder().
                                foo(1).
                                foo(2)).
                        myStruct(new Myarray.MyStruct.Builder().
                                a(-1).
                                b(-2))).

                myStructMap("one", new MyStructMap.Builder().
                        myInt(1).
                        myString("bull").
                        myIntDef(2).
                        myStringDef("bear").
                        anotherMap("anotherOne", new MyStructMap.AnotherMap.Builder().
                                anInt(3).
                                anIntDef(4)));
    }

    public static void assertVariableAccessValues(FunctionTestConfig config, String configId) {
        assertTrue(config.bool_with_def());
        assertEquals(5, config.int_val());
        assertEquals(-14, config.int_with_def());
        assertEquals(12345678901L, config.long_val());
        assertEquals(-9876543210L, config.long_with_def());
        assertEquals(41.23, config.double_val(), 0.000001);
        assertEquals(-12, config.double_with_def(), 0.000001);
        assertEquals("foo", config.string_val());
        assertEquals("bar and foo", config.stringwithdef());
        assertEquals(FunctionTestConfig.Enum_val.FOOBAR, config.enum_val());
        assertEquals(FunctionTestConfig.Enumwithdef.BAR2, config.enumwithdef());
        assertEquals(configId, config.refval());
        assertEquals(configId, config.refwithdef());
        assertEquals("etc", config.fileVal().value());
        assertEquals(1, config.boolarr().size());
        assertEquals(1, config.boolarr().size());  // new api with accessor for a List of the original Java type
        assertFalse(config.boolarr().get(0));  // new List api
        assertFalse(config.boolarr(0));        // short-hand
        assertEquals(0, config.intarr().size());
        assertEquals(2, config.longarr().size());
        assertEquals(Long.MAX_VALUE, config.longarr(0));
        assertEquals(Long.MIN_VALUE, config.longarr().get(1).longValue());
        assertEquals(2, config.doublearr().size());
        assertEquals(1, config.stringarr().size());
        assertEquals(1, config.enumarr().size());
        assertEquals(FunctionTestConfig.Enumarr.VALUES, config.enumarr().get(0));  // new List api, don't have to call value()
        assertEquals(3, config.refarr().size());
        assertEquals(1, config.fileArr().size());
        assertEquals(configId, config.refarr(0));
        assertEquals(":parent", config.refarr(1));
        assertEquals("parent:", config.refarr(2));
        assertEquals("bin", config.fileArr(0).value());

        assertEquals(1, config.intMap("one"));
        assertEquals(2, config.intMap("two"));
        assertEquals("first", config.stringMap("one"));
        assertEquals("/var", config.filemap("f1").value());
        assertEquals("/store", config.filemap("f2").value());
        assertEquals("basicFoo", config.basicStruct().foo());
        assertEquals(3, config.basicStruct().bar());  // new List api
        assertEquals(2, config.basicStruct().intArr().size());
        assertEquals(310, config.basicStruct().intArr().get(0).intValue());  // new List api
        assertEquals(311, config.basicStruct().intArr().get(1).intValue());  // new List api
        assertEquals(310, config.basicStruct().intArr(0));          // short-hand
        assertEquals("inner0", config.rootStruct().inner0().name());  // new List api
        assertEquals(11, config.rootStruct().inner0().index());
        assertEquals("inner1", config.rootStruct().inner1().name());
        assertEquals(12, config.rootStruct().inner1().index());
        assertEquals(2, config.rootStruct().innerArr().size());
        assertTrue(config.rootStruct().innerArr(0).boolVal());
        assertEquals("deep", config.rootStruct().innerArr(0).stringVal());
        assertFalse(config.rootStruct().innerArr(1).boolVal());
        assertEquals("blue a=\"escaped\"", config.rootStruct().innerArr(1).stringVal());

        assertEquals(2, config.myarray().size());  // new List api
        assertEquals(configId, config.myarray().get(0).refval());    // new List api
        assertEquals(configId, config.myarray(0).refval());          // short-hand
        assertEquals("file0", config.myarray(0).fileVal().value());
        assertEquals(1, config.myarray(0).myStruct().a());
        assertEquals(2, config.myarray(0).myStruct().b());
        assertEquals(configId, config.myarray(1).refval());
        assertEquals("file1", config.myarray(1).fileVal().value());
        assertEquals(-1, config.myarray(1).myStruct().a());
        assertEquals(-2, config.myarray(1).myStruct().b());

        assertEquals(1, config.myStructMap("one").myInt());
        assertEquals("bull", config.myStructMap("one").myString());
        assertEquals(2, config.myStructMap("one").myIntDef());
        assertEquals("bear", config.myStructMap("one").myStringDef());
        assertEquals(3, config.myStructMap("one").anotherMap("anotherOne").anInt());
        assertEquals(4, config.myStructMap("one").anotherMap("anotherOne").anIntDef());
    }

    private boolean callContainsFieldsFlaggedWithRestart(Class<?> configClass)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = configClass.getDeclaredMethod("containsFieldsFlaggedWithRestart");
        m.setAccessible(true);
        return (boolean) m.invoke(null);
    }

    private ChangesRequiringRestart callGetChangesRequiringRestart(ConfigInstance config1, ConfigInstance config2)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = config1.getClass().getDeclaredMethod("getChangesRequiringRestart", config2.getClass());
        m.setAccessible(true);
        return (ChangesRequiringRestart) m.invoke(config1, config2);
    }

    @Test
    void require_that_config_class_reports_any_restart_values() throws Exception {
        assertTrue(callContainsFieldsFlaggedWithRestart(RestartConfig.class));
        assertFalse(callContainsFieldsFlaggedWithRestart(IntConfig.class));
    }

    @Test
    void require_that_config_class_can_make_change_report() throws Exception {
        IntConfig noRestart1 = new IntConfig(new IntConfig.Builder().intVal(42));
        IntConfig noRestart2 = new IntConfig(new IntConfig.Builder().intVal(21));
        ChangesRequiringRestart report = callGetChangesRequiringRestart(noRestart1, noRestart2);
        assertFalse(report.needsRestart());

        RestartConfig config1 = new RestartConfig(new RestartConfig.Builder().intVal(42));
        RestartConfig config2 = new RestartConfig(new RestartConfig.Builder().intVal(21));
        report = callGetChangesRequiringRestart(config1, config1);
        assertFalse(report.needsRestart());
        report = callGetChangesRequiringRestart(config1, config2);
        assertTrue(report.needsRestart());

        FunctionTestConfig function1 = createVariableAccessConfigWithBuilder();
        FunctionTestConfig.Builder funcBuilder = createVariableAccessBuilder();
        funcBuilder.myStructMap.get("one").myInt(42);
        funcBuilder.int_val(100);
        funcBuilder.stringarr.set(0, "foo");
        funcBuilder.intMap.put("one", 42);
        funcBuilder.intMap.remove("two");
        funcBuilder.intMap.put("three", 3);
        funcBuilder.myarray.get(1).intval(17);
        funcBuilder.myarray.get(0).anotherarray.get(0).foo(32);
        funcBuilder.myarray.add(new Myarray.Builder().refval("refval").fileVal("fileval").urlVal(new UrlReference("urlval")).myStruct(new Myarray.MyStruct.Builder().a(4)));
        funcBuilder.myStructMap.put("new", new MyStructMap.Builder().myString("string").myInt(13));
        funcBuilder.basicStruct(new BasicStruct.Builder().bar(1234));
        FunctionTestConfig function2 = new FunctionTestConfig(funcBuilder);
        report = callGetChangesRequiringRestart(function1, function1);
        assertFalse(report.needsRestart());
        report = callGetChangesRequiringRestart(function1, function2);
        assertTrue(report.needsRestart());
        assertEquals("function-test", report.getName());
        assertTrue(
                report.toString().startsWith(
                        "# An int value\n" +
                                "# Also test that multiline comments\n" +
                                "# work.\n" +
                                "function-test.int_val has changed from 5 to 100\n" +
                                "function-test.stringarr[0] has changed from \"bar\" to \"foo\"\n" +
                                "# This is a map of ints.\n" +
                                "function-test.intMap{one} has changed from 1 to 42\n" +
                                "# This is a map of ints.\n" +
                                "function-test.intMap{two} with value 2 was removed\n" +
                                "# This is a map of ints.\n" +
                                "function-test.intMap{three} was added with value 3\n" +
                                "# A basic struct\n" +
                                "function-test.basicStruct.foo has changed from \"basicFoo\" to \"basic\"\n" +
                                "function-test.basicStruct.bar has changed from 3 to 1234\n" +
                                "function-test.basicStruct.intArr[0] with value 310 was removed\n" +
                                "function-test.basicStruct.intArr[1] with value 311 was removed\n" +
                                "function-test.myarray[0].anotherarray[0].foo has changed from 7 to 32\n" +
                                "# This is my array\n" +
                                "function-test.myarray[1].intval has changed from 5 to 17\n" +
                                "function-test.myarray[2] was added with value \n"
                )
        );

        assertTrue(
                report.toString().contains(
                        "function-test.myStructMap{one}.myInt has changed from 1 to 42\n" +
                                "function-test.myStructMap{new} was added with value \n"
                )
        );

        funcBuilder.myStructMap.remove("one");
        FunctionTestConfig function3 = new FunctionTestConfig(funcBuilder);
        report = callGetChangesRequiringRestart(function2, function3);
        assertEquals(1, report.getReportLines().size());
        assertTrue(
                report.toString().contains("function-test.myStructMap{one} with value \n")
        );
    }

}
