// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author freva
 */
public class SyncFileInfo {

    private final String bucketName;
    private final Path srcPath;
    private final Path destPath;
    private final boolean compressWithZstd;

    private SyncFileInfo(String bucketName, Path srcPath, Path destPath, boolean compressWithZstd) {
        this.bucketName = bucketName;
        this.srcPath = srcPath;
        this.destPath = destPath;
        this.compressWithZstd = compressWithZstd;
    }

    public String bucketName() {
        return bucketName;
    }

    public Path srcPath() {
        return srcPath;
    }

    public Path destPath() {
        return destPath;
    }

    public InputStream inputStream() throws IOException {
        InputStream is = Files.newInputStream(srcPath);
        if (compressWithZstd) return new ZstdCompressingInputStream(is, 4 << 20);
        return is;
    }


    public static SyncFileInfo tenantVespaLog(String bucketName, ApplicationId applicationId, HostName hostName, Path vespaLogFile) {
        return new SyncFileInfo(bucketName, vespaLogFile, destination(applicationId, hostName, "logs/vespa", vespaLogFile, ".zst"), true);
    }

    public static SyncFileInfo tenantAccessLog(String bucketName, ApplicationId applicationId, HostName hostName, Path accessLogFile) {
        return new SyncFileInfo(bucketName, accessLogFile, destination(applicationId, hostName, "logs/access", accessLogFile, null), false);
    }

    public static SyncFileInfo infrastructureVespaLog(String bucketName, HostName hostName, Path vespaLogFile) {
        return new SyncFileInfo(bucketName, vespaLogFile, destination(null, hostName, "logs/vespa", vespaLogFile, ".zst"), true);
    }

    public static SyncFileInfo infrastructureAccessLog(String bucketName, HostName hostName, Path accessLogFile) {
        return new SyncFileInfo(bucketName, accessLogFile, destination(null, hostName, "logs/access", accessLogFile, null), false);
    }

    private static Path destination(ApplicationId app, HostName hostName, String dir, Path filename, String extension) {
        StringBuilder sb = new StringBuilder(100).append('/');

        if (app == null) sb.append("infrastructure");
        else sb.append(app.tenant().value()).append('.').append(app.application().value()).append('.').append(app.instance().value());

        sb.append('/');
        for (char c: hostName.value().toCharArray()) {
            if (c == '.') break;
            sb.append(c);
        }

        sb.append('/').append(dir).append('/').append(filename.getFileName().toString());

        if (extension != null) sb.append(extension);

        return Paths.get(sb.toString());
    }
}
