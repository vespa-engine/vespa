// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author bratseth
 */
public class ZipStreamReader {

    private final ImmutableList<ZipEntryWithContent> entries;
    private final int maxEntrySizeInBytes;

    public ZipStreamReader(InputStream input, Predicate<String> entryNameMatcher, int maxEntrySizeInBytes) {
        this.maxEntrySizeInBytes = maxEntrySizeInBytes;
        try (ZipInputStream zipInput = new ZipInputStream(input)) {
            ImmutableList.Builder<ZipEntryWithContent> builder = new ImmutableList.Builder<>();
            ZipEntry zipEntry;
            while (null != (zipEntry = zipInput.getNextEntry())) {
                if (!entryNameMatcher.test(requireName(zipEntry.getName()))) continue;
                builder.add(new ZipEntryWithContent(zipEntry, readContent(zipInput)));
            }
            entries = builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException("IO error reading zip content", e);
        }
    }

    private byte[] readContent(ZipInputStream zipInput) {
        try (ByteArrayOutputStream bis = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            long size = 0;
            while ( -1 != (read = zipInput.read(buffer))) {
                size += read;
                if (size > maxEntrySizeInBytes) {
                    throw new IllegalArgumentException("Entry in zip content exceeded size limit of " +
                                                       maxEntrySizeInBytes + " bytes");
                }
                bis.write(buffer, 0, read);
            }
            return bis.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading from zipped content", e);
        }
    }

    public List<ZipEntryWithContent> entries() { return entries; }

    private static String requireName(String name) {
        IllegalArgumentException e = new IllegalArgumentException("Unexpected non-normalized path found in zip content");
        if (Arrays.asList(name.split("/")).contains("..")) throw e;
        if (!name.equals(Path.of(name).normalize().toString())) throw e;
        return name;
    }

    public static class ZipEntryWithContent {

        private final ZipEntry zipEntry;
        private final byte[] content;

        public ZipEntryWithContent(ZipEntry zipEntry, byte[] content) {
            this.zipEntry = zipEntry;
            this.content = content;
        }

        public ZipEntry zipEntry() { return zipEntry; }
        public byte[] content() { return content; }

    }

}
