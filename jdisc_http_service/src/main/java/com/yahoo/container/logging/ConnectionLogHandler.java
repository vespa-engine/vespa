// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

/**
 * @author mortent
 */
class ConnectionLogHandler {
    private final LogFileHandler<ConnectionLogEntry> logFileHandler;

    public ConnectionLogHandler(String clusterName, int queueSize, LogWriter<ConnectionLogEntry> logWriter) {
        logFileHandler = new LogFileHandler<>(
                LogFileHandler.Compression.ZSTD,
                String.format("logs/vespa/qrs/ConnectionLog.%s.%s", clusterName, "%Y%m%d%H%M%S"),
                "0 60 ...",
                String.format("ConnectionLog.%s", clusterName),
                queueSize,
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
