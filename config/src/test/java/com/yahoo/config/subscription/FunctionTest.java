// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.FunctionTestConfig;
import org.junit.Before;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Test most data types and features of the config client API,
 * e.g. parameter access, missing default values, and more.
 * <p/>
 * NOTE: this test does NOT test null default values.
 *
 * @author gjoranv
 */
@SuppressWarnings("deprecation")
public class FunctionTest {

    public static final String PATH = "src/test/resources/configs/function-test/";

    private FunctionTestConfig config;
    private ConfigSourceSet sourceSet = new ConfigSourceSet("function-test");

    public void configure(FunctionTestConfig config, ConfigSourceSet sourceSet) {
        this.config = config;
        assertEquals(this.sourceSet, sourceSet);
    }

    @Before
    public void resetConfig() {
        config = null;
    }

    @Test
    public void testVariableAccess() {
        assertNull(config);
        String configId = "file:" + PATH + "variableaccess.txt";
        ConfigGetter<FunctionTestConfig> getter = new ConfigGetter<>(FunctionTestConfig.class);
        assertVariableAccessValues(getter.getConfig(configId), configId);
    }

    @Test
    public void testDefaultValues() {
        assertNull(config);
        String configId = "file:" + PATH + "defaultvalues.txt";
        ConfigGetter<FunctionTestConfig> getter = new ConfigGetter<>(FunctionTestConfig.class);
        FunctionTestConfig config = getter.getConfig(configId);

        assertFalse(config.bool_val());
        assertFalse(config.bool_with_def());
        assertEquals(5, config.int_val());
        assertEquals(-545, config.int_with_def());
        assertEquals(1234567890123L, config.long_val());
        assertEquals(-50000000000L, config.long_with_def());
        assertEquals(41.23, config.double_val(), 0.000001);
        assertEquals(-6.43, config.double_with_def(), 0.000001);
        assertEquals("foo", config.string_val());
        assertEquals("foobar", config.stringwithdef());
        assertEquals(FunctionTestConfig.Enum_val.FOOBAR, config.enum_val());
        assertEquals(FunctionTestConfig.Enumwithdef.BAR2, config.enumwithdef());
        assertEquals(configId, config.refval());
        assertEquals(configId, config.refwithdef());
        assertEquals("vespa.log", config.fileVal().value());
        assertEquals(1, config.boolarr().size());
        assertEquals(0, config.intarr().size());
        assertEquals(0, config.longarr().size());
        assertEquals(2, config.doublearr().size());
        assertEquals(1, config.stringarr().size());
        assertEquals(1, config.enumarr().size());
        assertEquals(0, config.refarr().size());
        assertEquals(0, config.fileArr().size());

        assertEquals(3, config.basicStruct().bar());
        assertEquals(1, config.basicStruct().intArr().size());
        assertEquals(10, config.basicStruct().intArr(0));
        assertEquals(11, config.rootStruct().inner0().index());
        assertEquals(12, config.rootStruct().inner1().index());

        assertEquals(2, config.myarray().size());
        assertEquals(1, config.myarray(0).myStruct().a());
        assertEquals(-1, config.myarray(1).myStruct().a());
        assertEquals("command.com", config.myarray(0).fileVal().value());
        assertEquals("display.sys", config.myarray(1).fileVal().value());
    }


    @Test
    public void testRandomOrder() {
        assertNull(config);
        String configId = "file:" + PATH + "randomorder.txt";
        ConfigGetter<FunctionTestConfig> getter = new ConfigGetter<>(FunctionTestConfig.class);
        FunctionTestConfig config = getter.getConfig(configId);
        assertFalse(config.bool_val());
        assertTrue(config.bool_with_def());
        assertEquals(5, config.int_val());
        assertEquals(-14, config.int_with_def());
        assertEquals(666000666000L, config.long_val());
        assertEquals(-333000333000L, config.long_with_def());
        assertEquals(41.23, config.double_val(), 0.000001);
        assertEquals(-12, config.double_with_def(), 0.000001);
        assertEquals("foo", config.string_val());
        assertEquals("bar and foo", config.stringwithdef());
        assertEquals(FunctionTestConfig.Enum_val.FOOBAR, config.enum_val());
        assertEquals(FunctionTestConfig.Enumwithdef.BAR2, config.enumwithdef());
        assertEquals(configId, config.refval());
        assertEquals(configId, config.refwithdef());
        assertEquals("autoexec.bat", config.fileVal().value());
        assertEquals(1, config.boolarr().size());
        assertEquals(0, config.intarr().size());
        assertEquals(0, config.longarr().size());
        assertEquals(2, config.doublearr().size());
        assertEquals(1, config.stringarr().size());
        assertEquals(1, config.enumarr().size());
        assertEquals(0, config.refarr().size());
        assertEquals(0, config.fileArr().size());
        assertEquals(2, config.myarray().size());
    }

    @Test
    public void testLackingDefaults() throws IOException {
        attemptLacking("bool_val", false);
        attemptLacking("int_val", false);
        attemptLacking("long_val", false);
        attemptLacking("double_val", false);
        attemptLacking("string_val", false);
        attemptLacking("enum_val", false);
        attemptLacking("refval", false);
        attemptLacking("fileVal", false);

        attemptLacking("boolarr", true);
        attemptLacking("intarr", true);
        attemptLacking("longarr", true);
        attemptLacking("doublearr", true);
        attemptLacking("enumarr", true);
        attemptLacking("stringarr", true);
        attemptLacking("refarr", true);
        attemptLacking("fileArr", true);
        attemptLacking("myarray", true);

        attemptLacking("basicStruct.bar", false);
        attemptLacking("rootStruct.inner0.index", false);
        attemptLacking("rootStruct.inner1.index", false);
//        attemptLacking("rootStruct.innerArr[0].stringVal", false);

        attemptLacking("myarray[0].stringval", true);
        attemptLacking("myarray[0].refval", false);
        attemptLacking("myarray[0].anotherarray", true);
        attemptLacking("myarray[0].anotherarray", true);
        attemptLacking("myarray[0].myStruct.a", false);
    }

    private void attemptLacking(String param, boolean isArray) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(new File(PATH + "defaultvalues.txt")));
        StringBuilder config = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if ((line.length() > param.length()) &&
                    param.equals(line.substring(0, param.length())) &&
                    (line.charAt(param.length()) == ' ' || line.charAt(param.length()) == '[')) {
                // Ignore values matched
            } else {
                config.append(line).append("\n");
            }
        }
        //System.out.println("Config lacking " + param + "-> " + config + "\n");
        try {
            ConfigGetter<FunctionTestConfig> getter = new ConfigGetter<>(FunctionTestConfig.class);
            getter.getConfig("raw:\n" + config);
            if (isArray) {
                // Arrays are empty by default
                return;
            }
            fail("Expected to fail when not specifying value " + param + " without default");
        } catch (IllegalArgumentException expected) {
            if (isArray) {
                fail("Arrays should be empty by default.");
            }
        }
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
        assertEquals(false, config.boolarr().get(0));  // new List api
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
        assertEquals("pom.xml", config.pathArr(0).toString());
        assertEquals("pom.xml", config.pathMap("one").toString());

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
    }

}
