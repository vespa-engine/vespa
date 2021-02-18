// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author freva
 */
public class SyncFileInfo {

    private final Path source;
    private final URI destination;
    private final Compression uploadCompression;

    private SyncFileInfo(Path source, URI destination, Compression uploadCompression) {
        this.source = source;
        this.destination = destination;
        this.uploadCompression = uploadCompression;
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

    public static Optional<SyncFileInfo> forLogFile(URI uri, Path logFile) {
        String filename = logFile.getFileName().toString();
        Compression compression = Compression.NONE;
        String dir = null;

        if (filename.startsWith("vespa.log-")) {
            dir = "logs/vespa/";
            compression = Compression.ZSTD;
        } else if (filename.endsWith(".zst")) {
            if (filename.startsWith("JsonAccessLog.") || filename.startsWith("access"))
                dir = "logs/access/";
            else if (filename.startsWith("ConnectionLog."))
                dir = "logs/connection/";
        }

        if (dir == null) return Optional.empty();
        return Optional.of(new SyncFileInfo(
                logFile, uri.resolve(dir + logFile.getFileName() + compression.extension), compression));
    }

    public enum Compression {
        NONE(""), ZSTD(".zst");

        private final String extension;
        Compression(String extension) {
            this.extension = extension;
        }
    }
}
