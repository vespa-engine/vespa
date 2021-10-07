// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author freva
 */
public class SyncFileInfo {

    private final Path source;
    private final URI destination;
    private final Compression uploadCompression;
    private final Instant expiry;

    private SyncFileInfo(Path source, URI destination, Compression uploadCompression, Instant expiry) {
        this.source = source;
        this.destination = destination;
        this.uploadCompression = uploadCompression;
        this.expiry = expiry;
    }

    /** Source path of the file to sync */
    public Path source() {
        return source;
    }

    /** Remote URI to store the file at */
    public URI destination() {
        return destination;
    }

    /** Compression algorithm to use when uploading the file */
    public Compression uploadCompression() {
        return uploadCompression;
    }

    /** File expiry */
    public Optional<Instant> expiry() { return Optional.ofNullable(expiry); }

    public static Optional<SyncFileInfo> forLogFile(URI uri, Path logFile, boolean rotatedOnly) {
        String filename = logFile.getFileName().toString();
        Compression compression;
        String dir = null;

        if ((!rotatedOnly && filename.equals("vespa.log")) || filename.startsWith("vespa.log-")) {
            dir = "logs/vespa/";
            compression = Compression.ZSTD;
        } else {
            compression = filename.endsWith(".zst") ? Compression.NONE : Compression.ZSTD;
            if (rotatedOnly && compression != Compression.NONE)
                dir = null;
            else if (filename.startsWith("JsonAccessLog.") || filename.startsWith("access"))
                dir = "logs/access/";
            else if (filename.startsWith("ConnectionLog."))
                dir = "logs/connection/";
        }

        if (dir == null) return Optional.empty();
        return Optional.of(new SyncFileInfo(
                logFile, uri.resolve(dir + logFile.getFileName() + compression.extension), compression, null));
    }

    public static Optional<SyncFileInfo> forServiceDump(URI directory, Path file, Instant expiry) {
        String filename = file.getFileName().toString();
        List<String> filesToCompress = List.of(".bin", ".hprof", ".jfr", ".log");
        Compression compression = filesToCompress.stream().anyMatch(filename::endsWith) ? Compression.ZSTD : Compression.NONE;
        if (filename.startsWith(".")) return Optional.empty();
        URI location = directory.resolve(filename + compression.extension);
        return Optional.of(new SyncFileInfo(file, location, compression, expiry));
    }

    public enum Compression {
        NONE(""), ZSTD(".zst");

        private final String extension;
        Compression(String extension) {
            this.extension = extension;
        }
    }
}
