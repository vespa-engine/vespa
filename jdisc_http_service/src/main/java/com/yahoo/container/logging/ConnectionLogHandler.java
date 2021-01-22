// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * @author mortent
 */
class ConnectionLogHandler {
    private final LogFileHandler<ConnectionLogEntry> logFileHandler;

    public ConnectionLogHandler(String clusterName, LogWriter<ConnectionLogEntry> logWriter) {
        logFileHandler = new LogFileHandler<>(
                LogFileHandler.Compression.ZSTD,
                String.format("logs/vespa/qrs/ConnectionLog.%s.%s", clusterName, "%Y%m%d%H%M%S"),
                "0 60 ...",
                String.format("ConnectionLog.%s", clusterName),
                logWriter);
    }

    public void log(ConnectionLogEntry entry) {
        logFileHandler.publish(entry);
    }

    public void shutdown() {
        logFileHandler.close();
        logFileHandler.shutdown();
    }
}
