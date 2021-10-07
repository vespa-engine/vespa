// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import com.yahoo.test.AppConfig;
import com.yahoo.test.FunctionTestConfig;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static com.yahoo.test.FunctionTestConfig.BasicStruct;
import static com.yahoo.test.FunctionTestConfig.Enum_val;
import static com.yahoo.test.FunctionTestConfig.Enumarr;
import static com.yahoo.test.FunctionTestConfig.Myarray;
import static com.yahoo.test.FunctionTestConfig.RootStruct;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class ConfigInstanceEqualsTest {
    FunctionTestConfig config1;
    FunctionTestConfig.Builder builder2;
    FunctionTestConfig config2;

    @Before
    public void reset() {
        config1 = new FunctionTestConfig(newBuilder());
        builder2 = newBuilder();
        config2 = new FunctionTestConfig(builder2);
    }

    @Test
    public void require_same_hashCode_for_equal_instances() {
        assertThat(config1.hashCode(), is(config2.hashCode()));
    }

    @Test
    public void require_true_for_equal_instances() {
        assertThat(config1, is(config2));
    }

    @Test
    public void require_false_for_null() {
        assertThat(config1, not((FunctionTestConfig) null));

    }

    @Test
    public void require_false_for_different_subclass() {
        assertFalse(config1.equals(new AppConfig(new AppConfig.Builder())));
    }

    @Test
    public void require_false_for_different_scalars_at_root_node() {
        assertThat(config1, not(new FunctionTestConfig(newBuilder().bool_val(true))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().int_val(0))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().long_val(0L))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().double_val(0.0))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().string_val(""))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().enum_val(Enum_val.FOO))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().refval(""))));
        assertThat(config1, not(new FunctionTestConfig(newBuilder().fileVal(""))));
    }

    @Test
    public void require_false_for_different_leaf_array_at_root_node() {
        builder2.longarr.set(0, 0L);
        assertThat(config1, not(new FunctionTestConfig(builder2)));
    }

    @Test
    public void require_false_for_different_scalar_in_struct() {
        builder2.basicStruct(new BasicStruct.Builder(config1.basicStruct()).bar(0));
        assertThat(config1, not(new FunctionTestConfig(builder2)));
    }

    @Test
    public void require_false_for_different_scalar_in_inner_array() {
        builder2.myarray.get(0).intval(0);
        assertThat(config1, not(new FunctionTestConfig(builder2)));
    }

    @Test
    public void require_false_for_different_leaf_array_in_inner_array() {
        builder2.myarray.get(0).stringval.set(0, "");
        assertThat(config1, not(new FunctionTestConfig(builder2)));
    }

    @Test
    public void require_equal_structs_for_equal_configs() {
        assertThat(config1.basicStruct(), is(config2.basicStruct()));
        assertThat(config1.rootStruct(), is(config2.rootStruct()));
        assertThat(config1.rootStruct().inner0(), is(config2.rootStruct().inner0()));
    }

    @Test
    public void require_equal_inner_arrays_for_equal_configs() {
        assertThat(config1.myarray(), is(config2.myarray()));
        assertThat(config1.myarray(0).anotherarray(), is(config2.myarray(0).anotherarray()));
    }

    @Test
    public void require_equal_inner_array_elements_for_equal_configs() {
        assertThat(config1.myarray(0), is(config2.myarray(0)));
        assertThat(config1.myarray(0).anotherarray(0), is(config2.myarray(0).anotherarray(0)));
    }

    @Test
    public void require_equal_leaf_arrays_for_equal_configs() {
        assertThat(config1.intarr(), is(config2.intarr()));
        assertThat(config1.boolarr(), is(config2.boolarr()));
        assertThat(config1.longarr(), is(config2.longarr()));
        assertThat(config1.doublearr(), is(config2.doublearr()));
        assertThat(config1.stringarr(), is(config2.stringarr()));
        assertThat(config1.enumarr(), is(config2.enumarr()));
        assertThat(config1.refarr(), is(config2.refarr()));
        assertThat(config1.fileArr(), is(config2.fileArr()));
    }

    private static FunctionTestConfig.Builder newBuilder() {
        FunctionTestConfig.Builder builder = new FunctionTestConfig.Builder();

        return builder.bool_val(false).
                int_val(5).
                long_val(12345678901L).
                double_val(41.23).
                string_val("foo").
                enum_val(Enum_val.FOOBAR).
                refval(":parent:").
                fileVal("etc").
                pathVal(FileReference.mockFileReferenceForUnitTesting(new File("pom.xml"))).
                urlVal(new UrlReference("http://docs.vespa.ai")).
                boolarr(false).
                longarr(9223372036854775807L).
                longarr(-9223372036854775808L).
                doublearr(2344.0).
                doublearr(123.0).
                stringarr("bar").
                enumarr(Enumarr.VALUES).
                refarr(Arrays.asList(":parent:", ":parent", "parent:")).  // test collection based setter
                fileArr("bin").
                urlArr(new UrlReference("http://docs.vespa.ai")).

                basicStruct(new BasicStruct.Builder().
                        foo("basicFoo").
                        bar(3).
                        intArr(310)).

                rootStruct(new RootStruct.Builder().
                        inner0(new RootStruct.Inner0.Builder().
                                index(11)).
                        inner1(new RootStruct.Inner1.Builder().
                                index(12)).
                        innerArr(new RootStruct.InnerArr.Builder().
                                boolVal(true).
                                stringVal("deep"))).

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
                                b(-2)));

    }
}
