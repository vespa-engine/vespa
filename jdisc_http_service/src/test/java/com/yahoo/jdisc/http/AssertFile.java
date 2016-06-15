// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;


/**
 * @author <a href="mailto:apurvak@yahoo-inc.com">Apurva Kumar</a>
 */

public class AssertFile {

    public static void assertContains(File logFile, String expected) throws IOException {
        String s = new String(
                Files.readAllBytes(Paths.get(logFile.getAbsolutePath())),
                StandardCharsets.UTF_8);
        assertThat(s, containsString(expected));
    }

    public static void assertNotContains(File logFile, String expected) throws IOException {
        String s = new String(
                Files.readAllBytes(Paths.get(logFile.getAbsolutePath())),
                StandardCharsets.UTF_8);
        assertThat(s, not(containsString(expected)));
    }
}
