// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.FileReference;
import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.foo.TestNodefsConfig;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static com.yahoo.foo.FunctionTestConfig.*;

/**
 * @author Ulf Lilleengen
 */
public class ConfigInstanceUtilTest {

    @Test
    public void require_that_builder_values_can_be_overridden_by_another_builder() {
        FunctionTestConfig.Builder destination = createVariableAccessBuilder();

        FunctionTestConfig.Builder source = new FunctionTestConfig.Builder()
                .int_val(-1)
                .intarr(0)
                .doublearr(0.0)
                .basicStruct(new FunctionTestConfig.BasicStruct.Builder()
                        .bar(-1)
                        .intArr(0))
                .myarray(new FunctionTestConfig.Myarray.Builder()
                        .intval(-1)
                        .refval("")
                        .fileVal("")
                        .myStruct(new FunctionTestConfig.Myarray.MyStruct.Builder()
                                .a(0)
                        ));

        ConfigInstanceUtil.setValues(destination, source);

        FunctionTestConfig result = new FunctionTestConfig(destination);
        assertThat(result.int_val(), is(-1));
        assertThat(result.string_val(), is("foo"));
        assertThat(result.intarr().size(), is(1));
        assertThat(result.intarr(0), is(0));
        assertThat(result.longarr().size(), is(2));
        assertThat(result.doublearr().size(), is(3));
        assertEquals(2344.0, result.doublearr(0), 0.01);
        assertEquals(123.0, result.doublearr(1), 0.01);
        assertEquals(0.0, result.doublearr(2), 0.01);
        assertThat(result.basicStruct().bar(), is(-1));
        assertThat(result.basicStruct().foo(), is("basicFoo"));
        assertThat(result.basicStruct().intArr().size(), is(3));
        assertThat(result.basicStruct().intArr(0), is(310));
        assertThat(result.basicStruct().intArr(1), is(311));
        assertThat(result.basicStruct().intArr(2), is(0));
        assertThat(result.myarray().size(), is(3));
        assertThat(result.myarray(2).intval(), is(-1));
        assertThat(result.myarray(2).refval(), is(""));
        assertThat(result.myarray(2).fileVal().value(), is(""));
        assertThat(result.myarray(2).myStruct().a(), is(0));

    }

    @Test(expected = ConfigurationRuntimeException.class)
    public void require_that_invalid_builders_fail() {
        ConfigInstanceUtil.setValues(new FakeBuilder(), new FakeBuilder());
    }

    private static class FakeBuilder implements ConfigBuilder {
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

                basicStruct(new FunctionTestConfig.BasicStruct.Builder().
                        foo("basicFoo").
                        bar(3).
                        intArr(310).intArr(311)).

                rootStruct(new FunctionTestConfig.RootStruct.Builder().
                        inner0(new FunctionTestConfig.RootStruct.Inner0.Builder().
                                index(11)).
                        inner1(new FunctionTestConfig.RootStruct.Inner1.Builder().
                                index(12)).
                        innerArr(new FunctionTestConfig.RootStruct.InnerArr.Builder().
                                boolVal(true).
                                stringVal("deep")).
                        innerArr(new FunctionTestConfig.RootStruct.InnerArr.Builder().
                                boolVal(false).
                                stringVal("blue a=\"escaped\""))).

                myarray(new FunctionTestConfig.Myarray.Builder().
                        intval(-5).
                        stringval("baah").
                        stringval("yikes").
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file0").
                        anotherarray(new FunctionTestConfig.Myarray.Anotherarray.Builder().
                                foo(7)).
                        myStruct(new FunctionTestConfig.Myarray.MyStruct.Builder().
                                a(1).
                                b(2))).

                myarray(new FunctionTestConfig.Myarray.Builder().
                        intval(5).
                        enumval(Myarray.Enumval.INNER).
                        refval(":parent:").
                        fileVal("file1").
                        anotherarray(new FunctionTestConfig.Myarray.Anotherarray.Builder().
                                foo(1).
                                foo(2)).
                        myStruct(new FunctionTestConfig.Myarray.MyStruct.Builder().
                                a(-1).
                                b(-2)));

    }

    @Test
    public void test_require_private_getter() {
        FunctionTestConfig.Builder builder = createVariableAccessBuilder();
        assertEquals(ConfigInstanceUtil.getField(builder, "int_val"), 5);
        FunctionTestConfig conf = new FunctionTestConfig(builder);
        assertEquals(conf.int_val(), 5);
    }

    @Test
    public void testGetNewInstanceErrorMessage() {
        ConfigPayloadBuilder payloadBuilder = new ConfigPayloadBuilder();
        try {
            ConfigInstanceUtil.getNewInstance(TestNodefsConfig.class, "id0", ConfigPayload.fromBuilder(payloadBuilder));
            assert(false);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Failed creating new instance of 'com.yahoo.foo.TestNodefsConfig' for config id 'id0':"));
        }
    }
}
