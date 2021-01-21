// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import java.util.logging.Logger;

/**
 * @author mortent
 */
class ConnectionLogHandler {
    public final Logger connection = Logger.getAnonymousLogger();
    private final LogFileHandler logFileHandler;

    public ConnectionLogHandler(String clusterName) {
        connection.setUseParentHandlers(false);

        LogFormatter lf = new LogFormatter();
        lf.messageOnly(true);
        logFileHandler = new LogFileHandler(
                LogFileHandler.Compression.ZSTD,
                String.format("logs/vespa/qrs/ConnectionLog.%s.%s", clusterName, "%Y%m%d%H%M%S"),
                "0 60 ...",
                String.format("ConnectionLog.%s", clusterName),
                lf);
        this.logFileHandler.setFormatter(lf);
        connection.addHandler(this.logFileHandler);
    }

    public void shutdown() {
        logFileHandler.close();
        connection.removeHandler(logFileHandler);
        logFileHandler.shutdown();
    }
}