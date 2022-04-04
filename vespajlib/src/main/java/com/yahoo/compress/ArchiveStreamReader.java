// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

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
                Path path = Path.fromString(requireNormalized(entry.getName()));
                if (isSymlink(entry)) throw new IllegalArgumentException("Archive entry " + path + " is a symbolic link, which is disallowed");
                if (entry.isDirectory()) continue;
                if (!options.pathPredicate.test(path.toString())) continue;

                long size = 0;
                byte[] buffer = new byte[2048];
                int read;
                while ((read = archiveInputStream.read(buffer)) != -1) {
                    totalRead += read;
                    size += read;
                    if (totalRead > options.sizeLimit) throw new IllegalArgumentException("Total size of archive exceeds size limit");
                    if (read > options.entrySizeLimit) {
                        if (!options.truncateEntry) throw new IllegalArgumentException("Size of entry " + path + " exceeded entry size limit");
                    } else {
                        outputStream.write(buffer, 0, read);
                    }
                }
                return new ArchiveFile(path, crc32(entry), size);
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
        private final OptionalLong crc32;
        private final long size;

        public ArchiveFile(Path name, OptionalLong crc32, long size) {
            this.path = Objects.requireNonNull(name);
            this.crc32 = Objects.requireNonNull(crc32);
            if (crc32.isPresent()) {
                requireNonNegative("crc32", crc32.getAsLong());
            }
            this.size = requireNonNegative("size", size);
        }

        /** The path of this file inside its containing archive */
        public Path path() {
            return path;
        }

        /** The CRC-32 checksum of this file, if any */
        public OptionalLong crc32() {
            return crc32;
        }

        /** The decompressed size of this file */
        public long size() {
            return size;
        }

        private static long requireNonNegative(String field, long n) {
            if (n < 0) throw new IllegalArgumentException(field + " cannot be negative, got " + n);
            return n;
        }

    }

    /** Get the CRC-32 checksum of given archive entry, if any */
    private static OptionalLong crc32(ArchiveEntry entry) {
        long crc32 = -1;
        if (entry instanceof ZipArchiveEntry) {
            crc32 = ((ZipArchiveEntry) entry).getCrc();
        }
        return crc32 > -1 ? OptionalLong.of(crc32) : OptionalLong.empty();
    }

    private static boolean isSymlink(ArchiveEntry entry) {
        if (entry instanceof ZipArchiveEntry) return ((ZipArchiveEntry) entry).isUnixSymlink();
        if (entry instanceof TarArchiveEntry) return ((TarArchiveEntry) entry).isSymbolicLink();
        // TODO: Add workaround for the poor symlink handling in compress library
        throw new IllegalArgumentException("Unsupported archive entry " + entry.getClass().getSimpleName() + ", cannot check for symbolic link");
    }

    private static String requireNormalized(String name) {
        for (var part : name.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Unexpected non-normalized path found in zip content: '" + name + "'");
            }
        }
        return name;
    }

    /** Options for reading entries of an archive */
    public static class Options {

        private long sizeLimit = 8 * (long) Math.pow(1024, 3); // 8 GB
        private long entrySizeLimit = Long.MAX_VALUE;
        private boolean truncateEntry = false;
        private Predicate<String> pathPredicate = (path) -> true;

        private Options() {}

        /** Returns the standard set of read options */
        public static Options standard() {
            return new Options();
        }

        /** Set the total size limit when decompressing entries. Default is 8 GB */
        public Options sizeLimit(long limit) {
            this.sizeLimit = limit;
            return this;
        }

        /** Set the size limit of a decompressed entry. Default is no limit */
        public Options entrySizeLimit(long limit) {
            this.entrySizeLimit = limit;
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

        /** Set a predicate that an archive file path must match in order to be extracted. Default is to extract all files */
        public Options pathPredicate(Predicate<String> predicate) {
            this.pathPredicate = predicate;
            return this;
        }

    }

}
