// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author freva
 */
public class SyncFileInfo {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd.HH-mm-ss").withZone(ZoneOffset.UTC);

    private final Path source;
    private final Function<String, URI> destination;
    private final Compression uploadCompression;
    private final Map<String, String> tags;
    private final Optional<Duration> minDurationBetweenSync;

    private SyncFileInfo(Path source, Function<String, URI> destination, Compression uploadCompression,
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
        return destination.apply("");
    }

    /** Returns a destination URI after adding a suffix to the base name of the filename. */
    public URI destinationWithBasenameSuffix(String suffix) {
        return destination.apply(suffix);
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
        final String dir;
        String remoteFilename = logFile.getFileName().toString();
        Duration minDurationBetweenSync = null;

        if (filename.startsWith("vespa.log")) {
            dir = "logs/vespa/";
            compression = Compression.ZSTD;
            if (filename.length() == 9) {
                if (!rotatedOnly) remoteFilename = "vespa.log-" + DATE_TIME_FORMATTER.format(new UnixPath(logFile).getLastModifiedTime());
                minDurationBetweenSync = rotatedOnly ? Duration.ofHours(1) : Duration.ZERO;
            }
        } else if (filename.startsWith("zookeeper.") && filename.endsWith(".log")) {
            compression = Compression.ZSTD;
            dir = "logs/zookeeper/";
            remoteFilename = rotatedOnly && filename.endsWith(".0.log") ? "zookeeper.log" :
                    "zookeeper.log-" + DATE_TIME_FORMATTER.format(new UnixPath(logFile).getLastModifiedTime());
            minDurationBetweenSync = filename.endsWith(".0.log") ? rotatedOnly ? Duration.ofHours(1) : Duration.ZERO : null;
        } else {
            compression = filename.endsWith(".zst") ? Compression.NONE : Compression.ZSTD;
            if (rotatedOnly && compression != Compression.NONE)
                dir = null;
            else if (filename.contains(".metrics-proxy.")) // See AccessLogComponent.java for filename.
                dir = null;
            else if (filename.startsWith("JsonAccessLog.") || filename.startsWith("access"))
                dir = "logs/access/";
            else if (filename.startsWith("ConnectionLog."))
                dir = "logs/connection/";
            else
                dir = null;
        }

        if (dir == null) return Optional.empty();
        String finalRemoteFilename = remoteFilename;
        Function<String, URI> destination = suffix -> uri.resolve(dir + finalRemoteFilename + suffix + compression.extension);
        return Optional.of(new SyncFileInfo(logFile, destination, compression, defaultTags(owner), minDurationBetweenSync));
    }

    public static SyncFileInfo forServiceDump(URI destinationDir, Path file, Compression compression,
                                              ApplicationId owner, String assetClassification) {
        String filename = file.getFileName().toString();
        Function<String, URI> location = suffix -> destinationDir.resolve(filename + suffix + compression.extension);
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

    public boolean overwriteIfExists() {
        return minDurationBetweenSync.isPresent();
    }

    public enum Compression {
        NONE(""), ZSTD(".zst");

        private final String extension;
        Compression(String extension) {
            this.extension = extension;
        }
    }
}
