// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.compress.ArchiveStreamReader;
import com.yahoo.compress.ArchiveStreamReader.ArchiveFile;
import com.yahoo.compress.ArchiveStreamReader.Options;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A list of entries read from a ZIP archive, and their contents.
 *
 * @author bratseth
 */
public class ZipEntries {

    private final List<ZipEntryWithContent> entries;

    private ZipEntries(List<ZipEntryWithContent> entries) {
        this.entries = List.copyOf(Objects.requireNonNull(entries));
    }

    /** Read ZIP entries from inputStream */
    public static ZipEntries from(byte[] zip, Predicate<String> entryNameMatcher, int maxEntrySizeInBytes, boolean throwIfEntryExceedsMaxSize) {

        Options options = Options.standard()
                                 .pathPredicate(entryNameMatcher)
                                 .maxSize(2 * (long) Math.pow(1024, 3)) // 2 GB
                                 .maxEntrySize(maxEntrySizeInBytes)
                                 .maxEntries(1024)
                                 .truncateEntry(!throwIfEntryExceedsMaxSize);
        List<ZipEntryWithContent> entries = new ArrayList<>();
        try (ArchiveStreamReader reader = ArchiveStreamReader.ofZip(new ByteArrayInputStream(zip), options)) {
            ArchiveFile file;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((file = reader.readNextTo(baos)) != null) {
                entries.add(new ZipEntryWithContent(file.path().toString(),
                                                    Optional.of(baos.toByteArray()).filter(b -> b.length > 0),
                                                    file.size()));
                baos.reset();
            }
        }
        return new ZipEntries(entries);
    }

    public static byte[] readFile(byte[] zip, String name, int maxEntrySizeInBytes) {
        return from(zip, name::equals, maxEntrySizeInBytes, true).asList().get(0).contentOrThrow();
    }

    public List<ZipEntryWithContent> asList() { return entries; }

    public static class ZipEntryWithContent {

        private final String name;
        private final Optional<byte[]> content;
        private final long size;

        public ZipEntryWithContent(String name, Optional<byte[]> content, long size) {
            this.name = name;
            this.content = content;
            this.size = size;
        }

        public String name() { return name; }
        public byte[] contentOrThrow() { return content.orElseThrow(() -> new NoSuchElementException("'" + name + "' has no content")); }
        public Optional<byte[]> content() { return content; }
        public long size() { return size; }
    }

}
