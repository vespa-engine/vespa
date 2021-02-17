// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * @author freva
 */
public class SyncFileInfo {

    private final String bucketName;
    private final Path srcPath;
    private final Path destPath;
    private final Compression uploadCompression;

    private SyncFileInfo(String bucketName, Path srcPath, Path destPath, Compression uploadCompression) {
        this.bucketName = bucketName;
        this.srcPath = srcPath;
        this.destPath = destPath;
        this.uploadCompression = uploadCompression;
    }

    public String bucketName() {
        return bucketName;
    }

    /** Source path of the file to sync */
    public Path srcPath() {
        return srcPath;
    }

    /** Remote path to store the file at */
    public Path destPath() {
        return destPath;
    }

    /** Compression algorithm to use when uploading the file */
    public Compression uploadCompression() {
        return uploadCompression;
    }

    public static Optional<SyncFileInfo> tenantLog(String bucketName, ApplicationId applicationId, HostName hostName, Path logFile) {
        String filename = logFile.getFileName().toString();
        Compression compression = Compression.NONE;
        String dir = null;

        if (filename.startsWith("vespa.log-")) {
            dir = "logs/vespa";
            compression = Compression.ZSTD;
        } else if (filename.endsWith(".zst")) {
            if (filename.startsWith("JsonAccessLog.") || filename.startsWith("access"))
                dir = "logs/access";
            else if (filename.startsWith("ConnectionLog."))
                dir = "logs/connection";
        }

        if (dir == null) return Optional.empty();
        return Optional.of(new SyncFileInfo(
                bucketName, logFile, destination(applicationId, hostName, dir, logFile, compression), compression));
    }

    public static SyncFileInfo infrastructureVespaLog(String bucketName, HostName hostName, Path vespaLogFile) {
        Compression compression = Compression.ZSTD;
        return new SyncFileInfo(bucketName, vespaLogFile, destination(null, hostName, "logs/vespa", vespaLogFile, compression), compression);
    }

    private static Path destination(ApplicationId app, HostName hostName, String dir, Path filename, Compression uploadCompression) {
        StringBuilder sb = new StringBuilder(100);

        if (app == null) sb.append("infrastructure");
        else sb.append(app.tenant().value()).append('.').append(app.application().value()).append('.').append(app.instance().value());

        sb.append('/');
        for (char c: hostName.value().toCharArray()) {
            if (c == '.') break;
            sb.append(c);
        }

        sb.append('/').append(dir).append('/').append(filename.getFileName().toString());

        if (uploadCompression.extension != null) sb.append(uploadCompression.extension);

        return Paths.get(sb.toString());
    }

    public enum Compression {
        NONE(null), ZSTD(".zst");

        private final String extension;
        Compression(String extension) {
            this.extension = extension;
        }
    }
}
