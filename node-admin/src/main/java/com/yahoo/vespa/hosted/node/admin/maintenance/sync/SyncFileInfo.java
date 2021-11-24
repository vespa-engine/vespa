// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author freva
 */
public class SyncFileInfo {

    private final Path source;
    private final URI destination;
    private final Compression uploadCompression;
    private final Map<String, String> tags;
    private final Optional<Duration> minDurationBetweenSync;

    private SyncFileInfo(Path source, URI destination, Compression uploadCompression,
                         Map<String, String> tags, Duration minDurationBetweenSyncOrNull) {
        this.source = source;
        this.destination = destination;
        this.uploadCompression = uploadCompression;
        this.tags = Map.copyOf(tags);
        this.minDurationBetweenSync = Optional.ofNullable(minDurationBetweenSyncOrNull);
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

    public Map<String, String> tags() { return tags; }

    public Optional<Duration> minDurationBetweenSync() { return minDurationBetweenSync; }

    public static Optional<SyncFileInfo> forLogFile(URI uri, Path logFile, boolean rotatedOnly, ApplicationId owner) {
        String filename = logFile.getFileName().toString();
        Compression compression;
        String dir = null;
        Duration minDurationBetweenSync = null;

        if (filename.startsWith("vespa.log")) {
            dir = "logs/vespa/";
            compression = Compression.ZSTD;
            minDurationBetweenSync = filename.length() == 9 ? rotatedOnly ? Duration.ofHours(1) : Duration.ZERO : null;
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
                logFile, uri.resolve(dir + logFile.getFileName() + compression.extension), compression, defaultTags(owner),
                minDurationBetweenSync));
    }

    public static SyncFileInfo forServiceDump(URI destinationDir, Path file, Compression compression,
                                              ApplicationId owner, String assetClassification) {
        String filename = file.getFileName().toString();
        URI location = destinationDir.resolve(filename + compression.extension);
        Map<String, String> tags = defaultTags(owner);
        if (assetClassification != null) {
            tags.put("vespa:AssetClassification", assetClassification);
        }
        return new SyncFileInfo(file, location, compression, tags, null);
    }

    private static Map<String, String> defaultTags(ApplicationId owner) {
        var tags = new HashMap<String, String>();
        tags.put("corp:Application", owner.toFullString());
        return tags;
    }

    public enum Compression {
        NONE(""), ZSTD(".zst");

        private final String extension;
        Compression(String extension) {
            this.extension = extension;
        }
    }
}
