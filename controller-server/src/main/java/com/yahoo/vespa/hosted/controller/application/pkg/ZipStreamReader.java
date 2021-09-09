// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author bratseth
 */
public class ZipStreamReader {

    private final List<ZipEntryWithContent> entries = new ArrayList<>();
    private final int maxEntrySizeInBytes;

    public ZipStreamReader(InputStream input, Predicate<String> entryNameMatcher, int maxEntrySizeInBytes, boolean throwIfEntryExceedsMaxSize) {
        this.maxEntrySizeInBytes = maxEntrySizeInBytes;
        try (ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry zipEntry;

            while (null != (zipEntry = zipInput.getNextEntry())) {
                if (!entryNameMatcher.test(requireName(zipEntry.getName()))) continue;
                entries.add(readContent(zipEntry, zipInput, throwIfEntryExceedsMaxSize));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("IO error reading zip content", e);
        }
    }

    /** Copies the zipped content from in to out, adding/overwriting an entry with the given name and content. */
    public static void transferAndWrite(OutputStream out, InputStream in, String name, byte[] content) {
        try (ZipOutputStream zipOut = new ZipOutputStream(out);
             ZipInputStream zipIn = new ZipInputStream(in)) {
            for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                if (entry.getName().equals(name))
                    continue;

                zipOut.putNextEntry(entry);
                zipIn.transferTo(zipOut);
                zipOut.closeEntry();
            }
            zipOut.putNextEntry(new ZipEntry(name));
            zipOut.write(content);
            zipOut.closeEntry();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ZipEntryWithContent readContent(ZipEntry zipEntry, ZipInputStream zipInput, boolean throwIfEntryExceedsMaxSize) {
        try (ByteArrayOutputStream bis = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            long size = 0;
            while ( -1 != (read = zipInput.read(buffer))) {
                size += read;
                if (size > maxEntrySizeInBytes) {
                    if (throwIfEntryExceedsMaxSize) throw new IllegalArgumentException(
                            "Entry in zip content exceeded size limit of " + maxEntrySizeInBytes + " bytes");
                } else bis.write(buffer, 0, read);
            }

            boolean hasContent = size <= maxEntrySizeInBytes;
            return new ZipEntryWithContent(zipEntry,
                    Optional.of(bis).filter(__ -> hasContent).map(ByteArrayOutputStream::toByteArray),
                    size);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading from zipped content", e);
        }
    }

    public List<ZipEntryWithContent> entries() { return Collections.unmodifiableList(entries); }

    private static String requireName(String name) {
        if (List.of(name.split("/")).contains("..") ||
            !trimTrailingSlash(name).equals(Path.of(name).normalize().toString())) {
            throw new IllegalArgumentException("Unexpected non-normalized path found in zip content: '" + name + "'");
        }
        return name;
    }

    private static String trimTrailingSlash(String name) {
        if (name.endsWith("/")) return name.substring(0, name.length() - 1);
        return name;
    }

    public static class ZipEntryWithContent {

        private final ZipEntry zipEntry;
        private final Optional<byte[]> content;
        private final long size;

        public ZipEntryWithContent(ZipEntry zipEntry, Optional<byte[]> content, long size) {
            this.zipEntry = zipEntry;
            this.content = content;
            this.size = size;
        }

        public ZipEntry zipEntry() { return zipEntry; }
        public byte[] contentOrThrow() { return content.orElseThrow(); }
        public Optional<byte[]> content() { return content; }
        public long size() { return size; }
    }

}
