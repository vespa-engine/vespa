// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import com.yahoo.test.AppConfig;
import com.yahoo.test.FunctionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

import static com.yahoo.test.FunctionTestConfig.BasicStruct;
import static com.yahoo.test.FunctionTestConfig.Enum_val;
import static com.yahoo.test.FunctionTestConfig.Enumarr;
import static com.yahoo.test.FunctionTestConfig.Myarray;
import static com.yahoo.test.FunctionTestConfig.RootStruct;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ConfigInstanceEqualsTest {

    FunctionTestConfig config1;
    FunctionTestConfig.Builder builder2;
    FunctionTestConfig config2;

    @BeforeEach
    public void reset() {
        config1 = new FunctionTestConfig(newBuilder());
        builder2 = newBuilder();
        config2 = new FunctionTestConfig(builder2);
    }

    @Test
    void require_same_hashCode_for_equal_instances() {
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void require_true_for_equal_instances() {
        assertEquals(config1, config2);
    }

    @Test
    void require_false_for_null() {
        assertNotEquals(null, config1);

    }

    @Test
    void require_false_for_different_subclass() {
        assertNotEquals(config1, new AppConfig(new AppConfig.Builder()));
    }

    @Test
    void require_false_for_different_scalars_at_root_node() {
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().bool_val(true)));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().int_val(0)));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().long_val(0L)));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().double_val(0.0)));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().string_val("")));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().enum_val(Enum_val.FOO)));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().refval("")));
        assertNotEquals(config1, new FunctionTestConfig(newBuilder().fileVal("")));
    }

    @Test
    void require_false_for_different_leaf_array_at_root_node() {
        builder2.longarr.set(0, 0L);
        assertNotEquals(config1, new FunctionTestConfig(builder2));
    }

    @Test
    void require_false_for_different_scalar_in_struct() {
        builder2.basicStruct(new BasicStruct.Builder(config1.basicStruct()).bar(0));
        assertNotEquals(config1, new FunctionTestConfig(builder2));
    }

    @Test
    void require_false_for_different_scalar_in_inner_array() {
        builder2.myarray.get(0).intval(0);
        assertNotEquals(config1, new FunctionTestConfig(builder2));
    }

    @Test
    void require_false_for_different_leaf_array_in_inner_array() {
        builder2.myarray.get(0).stringval.set(0, "");
        assertNotEquals(config1, new FunctionTestConfig(builder2));
    }

    @Test
    void require_equal_structs_for_equal_configs() {
        assertEquals(config1.basicStruct(), config2.basicStruct());
        assertEquals(config1.rootStruct(), config2.rootStruct());
        assertEquals(config1.rootStruct().inner0(), config2.rootStruct().inner0());
    }

    @Test
    void require_equal_inner_arrays_for_equal_configs() {
        assertEquals(config1.myarray(), config2.myarray());
        assertEquals(config1.myarray(0).anotherarray(), config2.myarray(0).anotherarray());
    }

    @Test
    void require_equal_inner_array_elements_for_equal_configs() {
        assertEquals(config1.myarray(0), config2.myarray(0));
        assertEquals(config1.myarray(0).anotherarray(0), config2.myarray(0).anotherarray(0));
    }

    @Test
    void require_equal_leaf_arrays_for_equal_configs() {
        assertEquals(config1.intarr(), config2.intarr());
        assertEquals(config1.boolarr(), config2.boolarr());
        assertEquals(config1.longarr(), config2.longarr());
        assertEquals(config1.doublearr(), config2.doublearr());
        assertEquals(config1.stringarr(), config2.stringarr());
        assertEquals(config1.enumarr(), config2.enumarr());
        assertEquals(config1.refarr(), config2.refarr());
        assertEquals(config1.fileArr(), config2.fileArr());
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
                modelVal(ModelReference.unresolved(Optional.of("my-model-id"),
                                                   Optional.of(new UrlReference("http://docs.vespa.ai")),
                                                   Optional.empty())).
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
                modelArr(ModelReference.unresolved(Optional.empty(),
                                                   Optional.of(new UrlReference("http://docs.vespa.ai")),
                                                   Optional.of(FileReference.mockFileReferenceForUnitTesting(new File("pom.xml"))))).

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
