// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class ZipBuilderTest {

    @Test
    void test() {
        Map<String, String> expected = new HashMap<>();
        expected.put("dir/myfile", "my content");
        expected.put("rootfile", "this is root");
        expected.put("dir/newfile", "new file");
        expected.put("dir/dir2/file", "nested file");

        try (ZipBuilder zipBuilder1 = new ZipBuilder(100);
             ZipBuilder zipBuilder2 = new ZipBuilder(1000)) {

            // Add the entries to both zip builders one by one
            Iterator<Map.Entry<String, String>> entries = expected.entrySet().iterator();
            for (int i = 0; entries.hasNext(); i++) {
                Map.Entry<String, String> entry = entries.next();
                (i % 2 == 0 ? zipBuilder1 : zipBuilder2)
                        .add(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
            }

            // Add the zipped data from zip1 to zip2
            zipBuilder2.add(zipBuilder1.toByteArray());

            Map<String, String> actual = unzipToMap(zipBuilder2.toByteArray());
            assertEquals(expected, actual);
        }
    }

    Map<String, String> unzipToMap(byte[] zippedContent) {
        Map<String, String> contents = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zippedContent))) {
            for (ZipEntry entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                if (entry.isDirectory()) continue;
                contents.put(entry.getName(), new String(zin.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read zipped content", e);
        }
        return contents;
    }

}