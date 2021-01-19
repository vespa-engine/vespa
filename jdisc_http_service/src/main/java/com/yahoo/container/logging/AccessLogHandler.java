// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

import java.util.logging.Logger;

/**
 * @author Bjorn Borud
 */
class AccessLogHandler {

    public final Logger access = Logger.getAnonymousLogger();
    private final LogFileHandler logFileHandler;

    AccessLogHandler(AccessLogConfig.FileHandler config) {
        access.setUseParentHandlers(false);

        LogFormatter lf = new LogFormatter();
        lf.messageOnly(true);
        logFileHandler = new LogFileHandler(toCompression(config), config.pattern(), config.rotation(), config.symlink(), lf);
        access.addHandler(this.logFileHandler);
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
        access.removeHandler(logFileHandler);

        if (logFileHandler!=null)
            logFileHandler.shutdown();
    }

    void rotateNow() {
        logFileHandler.rotateNow();
    }
}
