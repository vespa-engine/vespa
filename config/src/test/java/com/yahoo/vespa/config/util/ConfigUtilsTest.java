// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.util;

import com.yahoo.collections.Tuple2;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
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

        assertEquals(expectedMd5, ConfigUtils.getDefMd5(lines));

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
        assertEquals(expectedMd5, ConfigUtils.getDefMd5(lines));
    }

    @Test
    public void testGetChecksumWithComment() {
        String expectedMd5 = "4395db1dfbd977c4d74190d2d23396e2";
        String expectedXxhash64 = "4395db1dfbd977c4d74190d2d23396e2";
        List<String> lines = new ArrayList<>();

        // Create normalized lines
        lines.add("foo=\"1#hello\"");
        lines.add(""); //empty line should not affect md5sum

        assertEquals(expectedMd5, getMd5(lines));
        assertEquals(expectedXxhash64, getMd5(lines));

        lines.clear();

        // Check that comment character in string leads to a different md5 than the original
        lines.add("foo=\"1#hello and some more\"");
        String md5 = getMd5(lines);
        String xxhash64 = getXxhash64(lines);
        assertNotEquals(expectedMd5, md5);
        assertNotEquals(expectedXxhash64, xxhash64);

        // Check that added characters after comment character in string leads to a different md5 than above
        lines.add("foo=\"1#hello and some more and even more\"");
        assertNotEquals(md5, getMd5(lines));
        assertNotEquals(xxhash64, getXxhash64(lines));
    }

    @Test
    public void testGetMd5OfString() {
        String expectedMd5 = "c9246ed8c8ab55b1c463c501c84075e6";
        String expectedXxhash64 = "b89f402d53626490";
        String expectedChangedMd5 = "f6f81062ef5f024f1912798490ba7dfc";
        String expectedChangedXxhash64 = "e8c361d384889610";

        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        assertEquals(expectedMd5, ConfigUtils.getMd5(payload.toString(true)));
        assertEquals(expectedXxhash64, ConfigUtils.getXxhash64(new Utf8Array(Utf8.toBytes(payload.toString(true)))));
        payload.getSlime().get().setString("fabio", "bar");
        assertEquals(expectedChangedMd5, ConfigUtils.getMd5(payload.toString(true)));
        assertEquals(expectedChangedXxhash64, ConfigUtils.getXxhash64(new Utf8Array(Utf8.toBytes(payload.toString(true)))));
    }

    @Test
    public void testStripSpaces() {
        assertEquals("a b", ConfigUtils.stripSpaces("a   b"));
        assertEquals("\"a   b\"", ConfigUtils.stripSpaces("\"a   b\""));
        assertEquals("a b \"a   b\"", ConfigUtils.stripSpaces("a   b   \"a   b\""));
        assertEquals("a b", ConfigUtils.stripSpaces("a b"));
    }

    @Test
    public void testGetNamespace() {
        // namespace after version
        StringReader reader = new StringReader("version=1\nnamespace=a\nint a default=0");
        assertEquals("a", ConfigUtils.getDefNamespace(reader));

        // namespace first
        reader = new StringReader("namespace=a\nversion=1\nint a default=0");
        assertEquals("a", ConfigUtils.getDefNamespace(reader));

        // package after namespace
        reader = new StringReader("namespace=a\npackage=b\nint a default=0");
        assertEquals("b", ConfigUtils.getDefNamespace(reader));

        // package before namespace
        reader = new StringReader("package=b\nnamespace=a\nint a default=0");
        assertEquals("b", ConfigUtils.getDefNamespace(reader));

        // no actual package
        assertEquals("package (or namespace) must consist of one or more segments joined by single dots (.), " +
                     "each starting with a lowercase letter (a-z), and then containing one or more lowercase letters (a-z), " +
                     "digits (0-9), or underscores (_)",
                     assertThrows(IllegalArgumentException.class,
                                  () -> ConfigUtils.getDefNamespace(new StringReader("package= \t \nint a default=0")))
                             .getMessage());

        // too relaxed namespace
        assertEquals("package (or namespace) must consist of one or more segments joined by single dots (.), " +
                     "each starting with a lowercase letter (a-z), and then containing one or more lowercase letters (a-z), " +
                     "digits (0-9), or underscores (_)",
                     assertThrows(IllegalArgumentException.class,
                                  () -> ConfigUtils.getDefNamespace(new StringReader("namespace=a/b\nint a default=0")))
                             .getMessage());

        // No namespace
        reader = new StringReader("version=1\nint a default=0");
        assertEquals("", ConfigUtils.getDefNamespace(reader));

        // comment lines
        reader = new StringReader("#comment\nversion=1\n#comment2\nint a default=0");
        assertEquals("", ConfigUtils.getDefNamespace(reader));

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
        assertEquals("bar", tuple.first);
        assertEquals("foo", tuple.second);

        namespaceDotName = "foo.baz.bar";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertEquals("bar", tuple.first);
        assertEquals("foo.baz", tuple.second);

        // no namespace
        namespaceDotName = "bar";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertEquals("bar", tuple.first);
        assertEquals("", tuple.second);

        // no name
        namespaceDotName = "foo.";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertEquals("", tuple.first);
        assertEquals("foo", tuple.second);

        // no namespace
        namespaceDotName = ".bar";
        tuple = ConfigUtils.getNameAndNamespaceFromString(namespaceDotName);
        assertEquals("bar", tuple.first);
        assertEquals("", tuple.second);
    }

    @Test
    public void testCreateConfigDefinitionKeyFromZKString() {
        ConfigDefinitionKey def1 = ConfigUtils.createConfigDefinitionKeyFromZKString("bar.foo,1");
        assertEquals("foo", def1.getName());
        assertEquals("bar", def1.getNamespace());

        ConfigDefinitionKey def2 = ConfigUtils.createConfigDefinitionKeyFromZKString("bar.foo,");
        assertEquals("foo", def2.getName());
        assertEquals("bar", def2.getNamespace());

        ConfigDefinitionKey def3 = ConfigUtils.createConfigDefinitionKeyFromZKString("bar.foo");
        assertEquals("foo", def3.getName());
        assertEquals("bar", def3.getNamespace());
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
        assertEquals("app", def.getName());
        assertEquals("foo", def.getNamespace());

        try {
            def = ConfigUtils.createConfigDefinitionKeyFromDefFile(new File("src/test/resources/configs/def-files/testnamespace.def"));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
        assertEquals("testnamespace", def.getName());
        assertEquals("foo", def.getNamespace());

        try {
            byte[] content = IOUtils.readFileBytes(new File("src/test/resources/configs/def-files/app.def"));
            def = ConfigUtils.createConfigDefinitionKeyFromDefContent("app", content);
        } catch (IOException e) {
            fail();
        }
        assertEquals("app", def.getName());
        assertEquals("foo", def.getNamespace());

        try {
            byte[] content = IOUtils.readFileBytes(new File("src/test/resources/configs/def-files-nogen/app.def"));
            def = ConfigUtils.createConfigDefinitionKeyFromDefContent("app", content);
        } catch (IOException e) {
            fail();
        }
        assertEquals("app", def.getName());
        assertEquals("mynamespace", def.getNamespace());
    }

    /**
     * Computes Md5 hash of a list of strings. The only change to input lines before
     * computing md5 is to skip empty lines.
     *
     * @param lines A list of lines
     * @return the Md5 hash of the list, with lowercase letters
     */
    private static String getMd5(List<String> lines) {
        return ConfigUtils.getMd5(skipEmptyLines(lines));
    }

    /**
     * Computes xxhash64 of a list of strings. The only change to input lines before
     * computing xxhash64 is to skip empty lines.
     *
     * @param lines A list of lines
     * @return the xxhash64 of the list, with lowercase letters
     */
    private static String getXxhash64(List<String> lines) {
        return ConfigUtils.getXxhash64(new Utf8Array(Utf8.toBytes(skipEmptyLines(lines))));
    }

    private static String skipEmptyLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            // Remove empty lines
            line = line.trim();
            if (line.length() > 0) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

}
