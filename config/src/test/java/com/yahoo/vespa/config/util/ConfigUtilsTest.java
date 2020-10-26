// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.util;

import com.yahoo.collections.Tuple2;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests ConfigUtils.
 *
 * @author hmusum
 */
public class ConfigUtilsTest {

    @Test
    public void testGetDefMd5() {
        String expectedMd5 = "a29038b17c727dabc572a967a508dc1f";
        List<String> lines = new ArrayList<>();

        // Create normalized lines
        lines.add("a");
        lines.add("foo=1 # a comment");
        lines.add("int a default=1 range = [,]");
        lines.add(""); //empty line should not affect md5sum
        lines.add("double b default=1.0 range = [,]");
        lines.add("collectiontype      enum { SINGLE, ARRAY, WEIGHTEDSET } default=SINGLE");

        assertThat(ConfigUtils.getDefMd5(lines), is(expectedMd5));

        lines.clear();

        // Test various normalizing features implemented by getMd5

        // Check that lines are trimmed
        lines.add("a ");
        // Check that trailing comments are trimmed
        lines.add("foo=1");
        // Check that upper and lower bounds for int and double ranges are set correctly
        lines.add("int a default=1 range = [-2147483648,2147483647]");
        lines.add("double b default=1.0 range = [-100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000," +
                "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000]");
        // check that space before commas are treated correctly
        lines.add("collectiontype      enum { SINGLE , ARRAY , WEIGHTEDSET } default=SINGLE");
        assertThat(ConfigUtils.getDefMd5(lines), is(expectedMd5));
    }

    @Test
    public void testGetMd5WithComment() {
        String expectedMd5 = "4395db1dfbd977c4d74190d2d23396e2";
        List<String> lines = new ArrayList<>();

        // Create normalized lines
        lines.add("foo=\"1#hello\"");
        lines.add(""); //empty line should not affect md5sum

        assertThat(getMd5(lines), is(expectedMd5));

        lines.clear();

        // Check that comment character in string leads to a different md5 than the original
        lines.add("foo=\"1#hello and some more\"");
        String md5 = getMd5(lines);
        assertThat(md5, is(not(expectedMd5)));

        // Check that added characters aft comment character in string leads to a different md5 than above
        lines.add("foo=\"1#hello and some more and even more\"");
        assertThat(getMd5(lines), is(not(md5)));
    }

    @Test
    public void testGetMd5OfPayload() {
        String expectedMd5 = "c9246ed8c8ab55b1c463c501c84075e6";
        String expectedChangedMd5 = "f6f81062ef5f024f1912798490ba7dfc";
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        System.out.println(payload);
        assertThat(ConfigUtils.getMd5(payload), is(expectedMd5));
        payload.getSlime().get().setString("fabio", "bar");
        System.out.println(payload);
        assertThat(ConfigUtils.getMd5(payload), is(expectedChangedMd5));
    }

    @Test
    public void testGetMd5OfString() {
        String expectedMd5 = "c9246ed8c8ab55b1c463c501c84075e6";
        String expectedChangedMd5 = "f6f81062ef5f024f1912798490ba7dfc";
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        System.out.println(payload);
        assertThat(ConfigUtils.getMd5(payload.toString(true)), is(expectedMd5));
        payload.getSlime().get().setString("fabio", "bar");
        System.out.println(payload);
        assertThat(ConfigUtils.getMd5(payload.toString(true)), is(expectedChangedMd5));
    }

    @Test
    public void testStripSpaces() {
        assertThat(ConfigUtils.stripSpaces("a   b"), is("a b"));
        assertThat(ConfigUtils.stripSpaces("\"a   b\""), is("\"a   b\""));
        assertThat(ConfigUtils.stripSpaces("a   b   \"a   b\""), is("a b \"a   b\""));
        assertThat(ConfigUtils.stripSpaces("a b"), is("a b"));
    }

    @Test
    public void testGetNamespace() {
        StringReader reader = new StringReader("version=1\nnamespace=a\nint a default=0");
        assertThat(ConfigUtils.getDefNamespace(reader), is("a"));
        // namespace first
        reader = new StringReader("namespace=a\nversion=1\nint a default=0");
        assertThat(ConfigUtils.getDefNamespace(reader), is("a"));

        // No namespace
        reader = new StringReader("version=1\nint a default=0");
        assertThat(ConfigUtils.getDefNamespace(reader), is(""));

        // comment lines
        reader = new StringReader("#comment\nversion=1\n#comment2\nint a default=0");
        assertThat(ConfigUtils.getDefNamespace(reader), is(""));

        try {
            ConfigUtils.getDefNamespace(null);
            fail();
        } catch (IllegalArgumentException e) {
            //
        }
    }

    @Test
    public void testNamespaceDotNames() {
        String namespaceDotName = "foo.bar";
        Tuple2<String, String> tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertThat(tuple.first, is("bar"));
        assertThat(tuple.second, is("foo"));

        namespaceDotName = "foo.baz.bar";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertThat(tuple.first, is("bar"));
        assertThat(tuple.second, is("foo.baz"));

        // no namespace
        namespaceDotName = "bar";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertThat(tuple.first, is("bar"));
        assertThat(tuple.second, is(""));

        // no name
        namespaceDotName = "foo.";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertThat(tuple.first, is(""));
        assertThat(tuple.second, is("foo"));

        // no namespace
        namespaceDotName = ".bar";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertThat(tuple.first, is("bar"));
        assertThat(tuple.second, is(""));
    }

    @Test
    public void testCreateConfigDefinitionKeyFromZKString() {
        ConfigDefinitionKey def1 = ConfigUtils.createConfigDefinitionKeyFromZKString("bar.foo,1");
        assertThat(def1.getName(), is("foo"));
        assertThat(def1.getNamespace(), is("bar"));

        ConfigDefinitionKey def2 = ConfigUtils.createConfigDefinitionKeyFromZKString("bar.foo,");
        assertThat(def2.getName(), is("foo"));
        assertThat(def2.getNamespace(), is("bar"));

        ConfigDefinitionKey def3 = ConfigUtils.createConfigDefinitionKeyFromZKString("bar.foo");
        assertThat(def3.getName(), is("foo"));
        assertThat(def3.getNamespace(), is("bar"));
    }

    @Test
    public void testCreateConfigDefinitionKeyFromDefFile() {
        ConfigDefinitionKey def = null;
        try {
            def = ConfigUtils.createConfigDefinitionKeyFromDefFile(new File("src/test/resources/configs/def-files/app.def"));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
        assertThat(def.getName(), is("app"));
        assertThat(def.getNamespace(), is("foo"));

        try {
            def = ConfigUtils.createConfigDefinitionKeyFromDefFile(new File("src/test/resources/configs/def-files/testnamespace.def"));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
        assertThat(def.getName(), is("testnamespace"));
        assertThat(def.getNamespace(), is("foo"));

        try {
            byte[] content = IOUtils.readFileBytes(new File("src/test/resources/configs/def-files/app.def"));
            def = ConfigUtils.createConfigDefinitionKeyFromDefContent("app", content);
        } catch (IOException e) {
            fail();
        }
        assertThat(def.getName(), is("app"));
        assertThat(def.getNamespace(), is("foo"));

        try {
            byte[] content = IOUtils.readFileBytes(new File("src/test/resources/configs/def-files-nogen/app.def"));
            def = ConfigUtils.createConfigDefinitionKeyFromDefContent("app", content);
        } catch (IOException e) {
            fail();
        }
        assertThat(def.getName(), is("app"));
        assertThat(def.getNamespace(), is("mynamespace"));
    }

    /**
     * Computes Md5 hash of a list of strings. The only change to input lines before
     * computing md5 is to skip empty lines.
     *
     * @param lines A list of lines
     * @return the Md5 hash of the list, with lowercase letters
     */
    private static String getMd5(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            // Remove empty lines
            line = line.trim();
            if (line.length() > 0) {
                sb.append(line).append("\n");
            }
        }
        return ConfigUtils.getMd5(sb.toString());
    }

}
