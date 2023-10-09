// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.AppConfig;
import com.yahoo.config.Md5testConfig;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests that does not yet have a specific home due to removed classes, obsolete features etc.
 *
 * @author vegardh
 */
public class MiscTestCase {

    /**
     * Verifies that the md5 sum computed on the server is equal to that in the generated class.
     *
     * @throws java.io.IOException if an error in zk
     */
    @Test
    public void testGetDefMd5() throws IOException {
        String defDir = "src/test/resources/configdefinitions/";
        assertEquals(AppConfig.CONFIG_DEF_MD5, ConfigUtils.getDefMd5(file2lines(new File(defDir + "config.app.def"))));
        assertEquals(Md5testConfig.CONFIG_DEF_MD5, ConfigUtils.getDefMd5(file2lines(new File(defDir + "config.md5test.def"))));
    }

    private static List<String> file2lines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        LineNumberReader in = new LineNumberReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    @Test
    public void testMd5StripSpaces() {
        assertEquals("", ConfigUtils.stripSpaces(""));
        assertEquals("foo", ConfigUtils.stripSpaces("foo"));
        assertEquals(" foo", ConfigUtils.stripSpaces(" foo"));
        assertEquals("bar ", ConfigUtils.stripSpaces("bar "));
        assertEquals("bar ", ConfigUtils.stripSpaces("bar  "));
        assertEquals("b ar", ConfigUtils.stripSpaces("b  \t ar"));
        assertEquals("bar foo", ConfigUtils.stripSpaces("bar\t\tfoo"));
        assertEquals("blabla string default=\"\t\"", ConfigUtils.stripSpaces("blabla string default=\"\t\""));
        assertEquals("blabla string default=\"foo\tbar\"", ConfigUtils.stripSpaces("blabla string default=\"foo\tbar\""));
        assertEquals("blabla string default=\" \t  \"", ConfigUtils.stripSpaces("blabla string default=\" \t  \""));
    }
}
