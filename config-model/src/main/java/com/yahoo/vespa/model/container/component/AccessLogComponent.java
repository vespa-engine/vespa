// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.container.logging.VespaAccessLog;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.osgi.provider.model.ComponentModel;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public final class AccessLogComponent extends SimpleComponent implements AccessLogConfig.Producer {

    public enum AccessLogType { queryAccessLog, yApacheAccessLog, jsonAccessLog }

    private final String fileNamePattern;
    private final String rotationInterval;
    private final Boolean compression;
    private final boolean isHostedVespa;
    private final String symlinkName;

    public AccessLogComponent(AccessLogType logType, String clusterName, boolean isHostedVespa)
    {
        this(logType,
                String.format("logs/vespa/qrs/%s.%s.%s", capitalize(logType.name()), clusterName, "%Y%m%d%H%M%S"),
                null, null, isHostedVespa,
                capitalize(logType.name()) + "." + clusterName);
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public AccessLogComponent(AccessLogType logType,
                              String fileNamePattern,
                              String rotationInterval,
                              Boolean compressOnRotation,
                              boolean isHostedVespa,
                              String symlinkName)
    {
        super(new ComponentModel(accessLogClass(logType), null, "container-core", null));
        this.fileNamePattern = fileNamePattern;
        this.rotationInterval = rotationInterval;
        this.compression = compressOnRotation;
        this.isHostedVespa = isHostedVespa;
        this.symlinkName = symlinkName;

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

        return builder;
    }

    public String getFileNamePattern() {
        return fileNamePattern;
    }
}
