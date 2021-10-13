// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final Instant expiry;
    private final Map<String, String> tags;

    private SyncFileInfo(Path source, URI destination, Compression uploadCompression, Instant expiry,
                         Map<String, String> tags) {
        this.source = source;
        this.destination = destination;
        this.uploadCompression = uploadCompression;
        this.expiry = expiry;
        this.tags = Map.copyOf(tags);
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

    public Map<String, String> tags() { return tags; }

    public static Optional<SyncFileInfo> forLogFile(URI uri, Path logFile, boolean rotatedOnly, ApplicationId owner) {
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
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        return Optional.of(new SyncFileInfo(
                logFile, uri.resolve(dir + logFile.getFileName() + compression.extension), compression, expiry, defaultTags(owner)));
    }

    public static SyncFileInfo forServiceDump(URI destinationDir, Path file, Instant expiry, Compression compression,
                                              ApplicationId owner, String assetClassification) {
        String filename = file.getFileName().toString();
        URI location = destinationDir.resolve(filename + compression.extension);
        Map<String, String> tags = defaultTags(owner);
        if (assetClassification != null) {
            tags.put("vespa:AssetClassification", assetClassification);
        }
        return new SyncFileInfo(file, location, compression, expiry, tags);
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
