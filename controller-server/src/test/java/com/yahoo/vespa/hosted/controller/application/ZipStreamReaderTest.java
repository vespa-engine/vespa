// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class ZipStreamReaderTest {

    @Test
    public void test_size_limit() {
        Map<String, String> entries = Map.of("foo.xml", "foobar");
        try {
            new ZipStreamReader(new ByteArrayInputStream(zip(entries)), "foo.xml"::equals, 1);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        entries = Map.of("foo.xml", "foobar",
                         "foo.zip", "0".repeat(100) // File not extracted and thus not subject to size limit
        );
        ZipStreamReader reader = new ZipStreamReader(new ByteArrayInputStream(zip(entries)), "foo.xml"::equals,10);
        byte[] extracted = reader.entries().get(0).content();
        assertEquals("foobar", new String(extracted, StandardCharsets.UTF_8));
    }

    private static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return zip.toByteArray();
    }

}
