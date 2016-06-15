// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


public class RunnerTest {

    @Test
    public void testAddFeedTag() throws IOException {
        InputStream stream = new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8));
        InputStream streamProcessed = Runner.addVespafeedTag(stream);
        assertThat(convertStreamToString(streamProcessed), is("<vespafeed>foo</vespafeed>"));
    }

    private static String convertStreamToString(java.io.InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        while (true) {
            int character = inputStream.read();
            if (character == -1) {
                inputStream.close();
                return builder.toString();
            }
            builder.append((char)character);
        }
    }
}