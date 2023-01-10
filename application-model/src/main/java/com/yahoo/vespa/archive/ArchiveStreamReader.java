// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.archive;

import com.yahoo.path.Path;
import com.yahoo.yolean.Exceptions;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * Helper class for safely reading files from a compressed archive.
 *
 * @author mpolden
 */
public class ArchiveStreamReader implements AutoCloseable {

    private final ArchiveInputStream archiveInputStream;
    private final Options options;

    private long totalRead = 0;
    private long entriesRead = 0;

    private ArchiveStreamReader(ArchiveInputStream archiveInputStream, Options options) {
        this.archiveInputStream = Objects.requireNonNull(archiveInputStream);
        this.options = Objects.requireNonNull(options);
    }

    /** Create reader for an inputStream containing a tar.gz file */
    public static ArchiveStreamReader ofTarGzip(InputStream inputStream, Options options) {
        return new ArchiveStreamReader(new TarArchiveInputStream(Exceptions.uncheck(() -> new GZIPInputStream(inputStream))), options);
    }

    /** Create reader for an inputStream containing a ZIP file */
    public static ArchiveStreamReader ofZip(InputStream inputStream, Options options) {
        return new ArchiveStreamReader(new ZipArchiveInputStream(inputStream), options);
    }

    /**
     * Read the next file in this archive and write it to given outputStream. Returns information about the read archive
     * file, or null if there are no more files to read.
     */
    public ArchiveFile readNextTo(OutputStream outputStream) {
        ArchiveEntry entry;
        try {
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                Path path = Path.fromString(requireNormalized(entry.getName(), options.allowDotSegment));
                if (isSymlink(entry)) throw new IllegalArgumentException("Archive entry " + path + " is a symbolic link, which is unsupported");
                if (entry.isDirectory()) continue;
                if (!options.pathPredicate.test(path.toString())) continue;
                if (++entriesRead > options.maxEntries) throw new IllegalArgumentException("Attempted to read more entries than entry limit of " + options.maxEntries);

                long size = 0;
                byte[] buffer = new byte[2048];
                int read;
                while ((read = archiveInputStream.read(buffer)) != -1) {
                    totalRead += read;
                    size += read;
                    if (totalRead > options.maxSize) throw new IllegalArgumentException("Total size of archive exceeds size limit of " + options.maxSize + " bytes");
                    if (read > options.maxEntrySize) {
                        if (!options.truncateEntry) throw new IllegalArgumentException("Size of entry " + path + " exceeded entry size limit of " + options.maxEntrySize + " bytes");
                    } else {
                        outputStream.write(buffer, 0, read);
                    }
                }
                return new ArchiveFile(path, size);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    @Override
    public void close() {
        Exceptions.uncheck(archiveInputStream::close);
    }

    /** Information about a file extracted from a compressed archive */
    public static class ArchiveFile {

        private final Path path;
        private final long size;

        public ArchiveFile(Path name, long size) {
            this.path = Objects.requireNonNull(name);
            this.size = requireNonNegative("size", size);
        }

        /** The path of this file inside its containing archive */
        public Path path() {
            return path;
        }

        /** The decompressed size of this file */
        public long size() {
            return size;
        }

    }

    private static boolean isSymlink(ArchiveEntry entry) {
        // Symlinks inside ZIP files are not part of the ZIP spec and are only supported by some implementations, such
        // as Info-ZIP.
        //
        // Commons Compress only has limited support for symlinks as they are only detected when the ZIP file is read
        // through org.apache.commons.compress.archivers.zip.ZipFile. This is not the case in this class, because it must
        // support reading ZIP files from generic input streams. The check below thus always returns false.
        if (entry instanceof ZipArchiveEntry zipEntry) return zipEntry.isUnixSymlink();
        if (entry instanceof TarArchiveEntry tarEntry) return tarEntry.isSymbolicLink();
        throw new IllegalArgumentException("Unsupported archive entry " + entry.getClass().getSimpleName() + ", cannot check for symbolic link");
    }

    private static String requireNormalized(String name, boolean allowDotSegment) {
        for (var part : name.split("/")) {
            if (part.isEmpty() || (!allowDotSegment && part.equals(".")) || part.equals("..")) {
                throw new IllegalArgumentException("Unexpected non-normalized path found in zip content: '" + name + "'");
            }
        }
        return name;
    }

    private static long requireNonNegative(String field, long n) {
        if (n < 0) throw new IllegalArgumentException(field + " cannot be negative, got " + n);
        return n;
    }

    /** Options for reading entries of an archive */
    public static class Options {

        private long maxSize = 8 * (long) Math.pow(1024, 3); // 8 GB
        private long maxEntrySize = Long.MAX_VALUE;
        private long maxEntries = Long.MAX_VALUE;
        private boolean truncateEntry = false;
        private boolean allowDotSegment = false;
        private Predicate<String> pathPredicate = (path) -> true;

        private Options() {}

        /** Returns the standard set of read options */
        public static Options standard() {
            return new Options();
        }

        /** Set the maximum total size of decompressed entries. Default is 8 GB */
        public Options maxSize(long size) {
            this.maxSize = requireNonNegative("size", size);
            return this;
        }

        /** Set the maximum size a decompressed entry. Default is no limit */
        public Options maxEntrySize(long size) {
            this.maxEntrySize = requireNonNegative("size", size);
            return this;
        }

        /** Set the maximum number of entries to decompress. Default is no limit */
        public Options maxEntries(long count) {
            this.maxEntries = requireNonNegative("count", count);
            return this;
        }

        /**
         * Set whether to truncate the content of an entry exceeding the configured size limit, instead of throwing.
         * Default is to throw.
         */
        public Options truncateEntry(boolean truncate) {
            this.truncateEntry = truncate;
            return this;
        }

        /** Set a predicate that an entry path must match in order to be extracted. Default is to extract all entries */
        public Options pathPredicate(Predicate<String> predicate) {
            this.pathPredicate = predicate;
            return this;
        }

        /** Set whether to allow single-dot segments in entry paths. Default is false */
        public Options allowDotSegment(boolean allow) {
            this.allowDotSegment = allow;
            return this;
        }

    }

}
