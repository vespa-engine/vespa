// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.container.core.AccessLogConfig.FileHandler.CompressionFormat;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.container.logging.VespaAccessLog;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public final class AccessLogComponent extends SimpleComponent implements AccessLogConfig.Producer {


    public enum AccessLogType { queryAccessLog, yApacheAccessLog, jsonAccessLog }
    public enum CompressionType { GZIP, ZSTD }

    private final String fileNamePattern;
    private final String rotationInterval;
    private final Boolean compression;
    private final boolean isHostedVespa;
    private final String symlinkName;
    private final CompressionType compressionType;
    private final int queueSize;
    private final Integer bufferSize;

    public AccessLogComponent(ContainerCluster<?> cluster, AccessLogType logType, CompressionType compressionType, String clusterName, boolean isHostedVespa)
    {
        this(logType, compressionType,
                String.format("logs/vespa/qrs/%s.%s.%s", capitalize(logType.name()), clusterName, "%Y%m%d%H%M%S"),
                null, null, isHostedVespa,
                capitalize(logType.name()) + "." + clusterName, -1,
                ((cluster instanceof ApplicationContainerCluster) ? 4*1024*1024 : null));
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public AccessLogComponent(AccessLogType logType,
                              CompressionType compressionType,
                              String fileNamePattern,
                              String rotationInterval,
                              Boolean compressOnRotation,
                              boolean isHostedVespa,
                              String symlinkName,
                              Integer queueSize,
                              Integer bufferSize)
    {
        super(new ComponentModel(accessLogClass(logType), null, "container-core", null));
        this.fileNamePattern = fileNamePattern;
        this.rotationInterval = rotationInterval;
        this.compression = compressOnRotation;
        this.isHostedVespa = isHostedVespa;
        this.symlinkName = symlinkName;
        this.compressionType = compressionType;
        this.queueSize = (queueSize == null) ? 256 : queueSize;
        this.bufferSize = bufferSize;

        if (fileNamePattern == null)
            throw new RuntimeException("File name pattern required when configuring access log.");
    }

    private static String accessLogClass(AccessLogType logType) {
        switch (logType) {
            case queryAccessLog:
                return VespaAccessLog.class.getName();
            case jsonAccessLog:
                return JSONAccessLog.class.getName();
            default:
                throw new AssertionError();
        }
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
        switch (compressionType) {
            case GZIP:
                builder.compressionFormat(CompressionFormat.GZIP);
                break;
            case ZSTD:
                builder.compressionFormat(CompressionFormat.ZSTD);
                break;
            default:
                throw new IllegalArgumentException("Unknown compression type: " + compressionType);
        }
        return builder;
    }

    public String getFileNamePattern() {
        return fileNamePattern;
    }
}
