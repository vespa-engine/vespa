// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

/**
 * @author Bjorn Borud
 */
class AccessLogHandler {

    private final LogFileHandler<RequestLogEntry> logFileHandler;

    AccessLogHandler(AccessLogConfig.FileHandler config, LogWriter<RequestLogEntry> logWriter) {
        logFileHandler = new LogFileHandler<>(
                toCompression(config), config.bufferSize(), config.pattern(), config.rotation(),
                config.symlink(), queueSize(config), "request-logger", logWriter);
    }

    private static int queueSize(AccessLogConfig.FileHandler config) {
        if (config.queueSize() != -1) return config.queueSize();
        return Math.max(4096, Runtime.getRuntime().availableProcessors() * 256);
    }

    public void log(RequestLogEntry entry) {
        logFileHandler.publish(entry);
    }


    private LogFileHandler.Compression toCompression(AccessLogConfig.FileHandler config) {
        if (!config.compressOnRotation()) return LogFileHandler.Compression.NONE;
        switch (config.compressionFormat()) {
            case ZSTD: return LogFileHandler.Compression.ZSTD;
            case GZIP: return LogFileHandler.Compression.GZIP;
            default: throw new IllegalArgumentException(config.compressionFormat().toString());
        }
    }

    void shutdown() {
        logFileHandler.close();
        logFileHandler.shutdown();
    }
}
