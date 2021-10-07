// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author valerijf
 */
public class FormatInputStreamTest {
    @Test(expected=IllegalArgumentException.class)
    public void testWithGarbageText() throws IOException {
        String streamString = "This is neither XML nor JSON!";
        InputStream jsonStream = getInputStreamOf(streamString);
        FormatInputStream formatInputStream = new FormatInputStream(jsonStream, Optional.empty(), false);
    }

    @Test
    public void testWithFileInput() throws IOException {
        String fileString = "{\"format\": \"json\"}";
        File file = File.createTempFile("feeddata", "json");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(fileString);
        }

        FormatInputStream formatInputStream = new FormatInputStream(null, Optional.of(file.getAbsolutePath()), false);
        assertThat(fileString, is(convertStreamToString(formatInputStream.getInputStream())));
        assertThat(formatInputStream.getFormat(), is(FormatInputStream.Format.JSON));
    }

    @Test
    public void testPreferenceFileOverStream() throws IOException {
        String streamString = "something entirely different";
        String fileString = "{\"format\": \"json\"}";

        InputStream jsonStream = getInputStreamOf(streamString);
        File file = File.createTempFile("feeddata", "json");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(fileString);
        }

        FormatInputStream formatInputStream = new FormatInputStream(jsonStream, Optional.of(file.getAbsolutePath()), false);
        assertThat(fileString, is(convertStreamToString(formatInputStream.getInputStream())));
        assertThat(formatInputStream.getFormat(), is(FormatInputStream.Format.JSON));
    }

    @Test
    public void testSimpleJsonInputStream() throws IOException {
        String streamString = "{\"format\": \"json\"}";
        InputStream jsonStream = getInputStreamOf(streamString);
        FormatInputStream formatInputStream = new FormatInputStream(jsonStream, Optional.empty(), false);

        assertThat(streamString, is(convertStreamToString(formatInputStream.getInputStream())));
        assertThat(formatInputStream.getFormat(), is(FormatInputStream.Format.JSON));
    }

    @Test
    public void testSimpleXmlInputStream() throws IOException {
        String streamString = "<scope><tag>format</tag><value>xml</value></scope>";
        InputStream jsonStream = getInputStreamOf(streamString);
        FormatInputStream formatInputStream = new FormatInputStream(jsonStream, Optional.empty(), false);

        assertThat(streamString, is(convertStreamToString(formatInputStream.getInputStream())));
        assertThat(formatInputStream.getFormat(), is(FormatInputStream.Format.XML));
    }

    @Test
    public void testSparselyFormattedXml() throws IOException {
        String streamString = "               \t\t\n<scope>\n\n\n<tag>format</tag><value>xml</value></scope>";
        InputStream jsonStream = getInputStreamOf(streamString);
        FormatInputStream formatInputStream = new FormatInputStream(jsonStream, Optional.empty(), false);

        assertThat(streamString, is(convertStreamToString(formatInputStream.getInputStream())));
        assertThat(formatInputStream.getFormat(), is(FormatInputStream.Format.XML));
    }

    @Test
    public void testAddRootToXml() throws IOException {
        String streamString = "some random text";
        InputStream textStream = getInputStreamOf(streamString);
        FormatInputStream formatInputStream = new FormatInputStream(textStream, Optional.empty(), true);

        assertThat("<vespafeed>" + streamString + "</vespafeed>",
                is(convertStreamToString(formatInputStream.getInputStream())));
        assertThat(formatInputStream.getFormat(), is(FormatInputStream.Format.XML));
    }

    private static String convertStreamToString(InputStream inputStream) throws IOException {
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

    private static InputStream getInputStreamOf(String text) {
        return new ByteArrayInputStream(text.getBytes());
    }
}
