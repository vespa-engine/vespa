// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.FileReference;
import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.foo.MaptypesConfig;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigTransformer;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.foo.FunctionTestConfig.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
public class ConfigInstancePayloadTest {

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
                pathVal(FileReference.mockFileReferenceForUnitTesting(new File("src/test/resources/configs/def-files/function-test.def"))).
                boolarr(false).
                longarr(9223372036854775807L).
                longarr(-9223372036854775808L).
                doublearr(2344.0).
                doublearr(123.0).
                stringarr("bar").
                enumarr(Enumarr.VALUES).
                refarr(Arrays.asList(":parent:", ":parent", "parent:")).  // test collection based setter
                fileArr("bin").
                pathArr(FileReference.mockFileReferenceForUnitTesting(new File("pom.xml"))).
                intMap("one", 1).
                intMap("two", 2).
                intMap("dotted.key", 3).
                intMap("spaced key", 4).
                stringMap("one", "first").
                stringMap("double.dotted.key", "second").
                stringMap("double spaced key", "third").
                pathMap("one", FileReference.mockFileReferenceForUnitTesting(new File("pom.xml"))).
                basicStruct(new BasicStruct.Builder().
                        foo("basicFoo").
                        bar(3).
                        intArr(310).intArr(311)).

                rootStruct(new RootStruct.Builder().
                        inner0(b -> b.index(11)).
                        inner1(new RootStruct.Inner1.Builder().
                                index(12)).
                        innerArr(b -> b.boolVal(true).stringVal("deep")).
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
                        anotherarray(b -> b.foo(7)).
                        myStruct(new Myarray.MyStruct.Builder().
                                a(1).
                                b(2))).

                myarray(b -> b.
                        intval(5).
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file1").
                        anotherarray(bb -> bb.foo(1).foo(2)).
                        myStruct(bb -> bb.
                                 a(-1).
                                 b(-2))).

                myStructMap("one", new MyStructMap.Builder().
                        myInt(1).
                        myString("bull").
                        myIntDef(2).
                        myStringDef("bear").
                        anotherMap("anotherOne", b -> b.
                                   anInt(3).
                                   anIntDef(4)));
    }

    @Test
    public void config_builder_can_be_created_from_generic_payload() {
        FunctionTestConfig config = createVariableAccessConfigWithBuilder();
        ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(ConfigInstance.serialize(config));
        assertFunctionTestPayload(config, payload);
    }

    @Test
    public void config_builder_can_be_created_from_typed_payload() {
        FunctionTestConfig config = createVariableAccessConfigWithBuilder();
        Slime slime = new Slime();
        ConfigInstanceSerializer serializer = new ConfigInstanceSerializer(slime);
        ConfigInstance.serialize(config, serializer);
        assertFunctionTestPayload(config, new ConfigPayload(slime));
    }

    private void assertFunctionTestPayload(FunctionTestConfig expected, ConfigPayload payload) {
        try {
            System.out.println(payload.toString(false));
            FunctionTestConfig config2 = new FunctionTestConfig((FunctionTestConfig.Builder)new ConfigTransformer<>(FunctionTestConfig.class).toConfigBuilder(payload));
            assertEquals(expected, config2);
            assertEquals(ConfigInstance.serialize(expected), ConfigInstance.serialize(config2));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void function_test_payload_is_correctly_deserialized() {
        FunctionTestConfig orig = createVariableAccessConfigWithBuilder();
        List<String> lines = ConfigInstance.serialize(orig);
        ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(lines);
        FunctionTestConfig config = ConfigInstanceUtil.getNewInstance(FunctionTestConfig.class, "foo", payload);
        FunctionTest.assertVariableAccessValues(config, "foo");
    }

    @Test
    public void map_types_are_correctly_deserialized() {
        MaptypesConfig orig = createMapTypesConfig();
        List<String> lines = ConfigInstance.serialize(orig);
        ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(lines);
        System.out.println(payload.toString());
        MaptypesConfig config = ConfigInstanceUtil.getNewInstance(MaptypesConfig.class, "foo", payload);
        System.out.println(config);
        assertEquals(1, config.intmap().size());
        assertEquals(1337, config.intmap("foo"));
        assertNotNull(config.innermap("bar"));
        assertEquals(93, config.innermap("bar").foo());
        assertEquals(1, config.nestedmap().size());
        assertNotNull(config.nestedmap("baz"));
        assertEquals(1, config.nestedmap("baz").inner("foo"));
        assertEquals(2, config.nestedmap("baz").inner("bar"));
    }

    private MaptypesConfig createMapTypesConfig() {
        MaptypesConfig.Builder builder = new MaptypesConfig.Builder();
        builder.intmap("foo", 1337);
        MaptypesConfig.Innermap.Builder inner = new MaptypesConfig.Innermap.Builder();
        inner.foo(93);
        builder.innermap("bar", inner);
        MaptypesConfig.Nestedmap.Builder n1 = new MaptypesConfig.Nestedmap.Builder();
        n1.inner("foo", 1);
        n1.inner("bar", 2);
        builder.nestedmap("baz", n1);
        return new MaptypesConfig(builder);
    }
}
