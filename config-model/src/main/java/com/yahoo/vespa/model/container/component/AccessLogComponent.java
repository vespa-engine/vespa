// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.container.logging.VespaAccessLog;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;

import java.util.Objects;
import java.util.Optional;

import static com.yahoo.container.core.AccessLogConfig.FileHandler.CompressionFormat.Enum.GZIP;
import static com.yahoo.container.core.AccessLogConfig.FileHandler.CompressionFormat.Enum.ZSTD;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public final class AccessLogComponent extends SimpleComponent implements AccessLogConfig.Producer {


    public enum AccessLogType { queryAccessLog, jsonAccessLog }
    public enum CompressionType { GZIP, ZSTD }

    private final String fileNamePattern;
    private final String rotationInterval;
    private final Boolean compression;
    private final boolean isHostedVespa;
    private final String symlinkName;
    private final CompressionType compressionType;
    private final int queueSize;
    private final Integer bufferSize;

    public AccessLogComponent(ContainerCluster<?> cluster,
                              AccessLogType logType,
                              String compressionType,
                              Optional<String> clusterName,
                              boolean isHostedVespa) {
        // In hosted Vespa we do not use the clusterName when setting up application ContainerCluster logging
        this(logType,
                compressionType,
             String.format("logs/vespa/access/%s.%s",
                           capitalize(logTypeAndClusterName(logType, clusterName)),
                           "%Y%m%d%H%M%S"),
                null,
                null,
                isHostedVespa,
                capitalize(logTypeAndClusterName(logType, clusterName)),
                -1,
                ((cluster instanceof ApplicationContainerCluster) ? 4 << 20 : null));
    }

    public AccessLogComponent(AccessLogType logType,
                              String compressionType,
                              String fileNamePattern,
                              String rotationInterval,
                              Boolean compressOnRotation,
                              boolean isHostedVespa,
                              String symlinkName,
                              Integer queueSize,
                              Integer bufferSize) {
        super(new ComponentModel(accessLogClass(logType), null, null, null));
        this.fileNamePattern = Objects.requireNonNull(fileNamePattern, "File name pattern required when configuring access log");
        this.rotationInterval = rotationInterval;
        this.compression = compressOnRotation;
        this.isHostedVespa = isHostedVespa;
        this.symlinkName = symlinkName;
        this.compressionType = "zstd".equals(compressionType) ? CompressionType.ZSTD :CompressionType.GZIP;
        this.queueSize = (queueSize == null) ? 256 : queueSize;
        this.bufferSize = bufferSize;
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private static String logTypeAndClusterName(AccessLogType logType, Optional<String> clusterName) {
        return clusterName.isEmpty()
                ? logType.name()
                : logType.name() + "." + clusterName.get();
    }

    private static String accessLogClass(AccessLogType logType) {
        return switch (logType) {
            case queryAccessLog -> VespaAccessLog.class.getName();
            case jsonAccessLog -> JSONAccessLog.class.getName();
        };
    }

    @Override
    public void getConfig(AccessLogConfig.Builder builder) {
        builder.fileHandler(fileHandlerConfig());
    }

    private AccessLogConfig.FileHandler.Builder fileHandlerConfig() {
        AccessLogConfig.FileHandler.Builder builder = new AccessLogConfig.FileHandler.Builder();
        if (fileNamePattern != null)
            builder.pattern(fileNamePattern);
        if (rotationInterval != null)
            builder.rotation(rotationInterval);
        if (symlinkName != null)
            builder.symlink(symlinkName);
        if (compression != null) {
            builder.compressOnRotation(compression);
        } else if (isHostedVespa) {
            builder.compressOnRotation(true);
        }
        builder.queueSize(queueSize);
        if (bufferSize != null) {
            builder.bufferSize(bufferSize);
        }
        return switch (compressionType) {
            case GZIP -> builder.compressionFormat(GZIP);
            case ZSTD -> builder.compressionFormat(ZSTD);
        };
    }

    public String getFileNamePattern() { return fileNamePattern; }

}
