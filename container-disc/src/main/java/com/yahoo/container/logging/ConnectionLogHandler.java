// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.yahoo.text.Text;

/**
 * @author mortent
 */
class ConnectionLogHandler {
    private final LogFileHandler<ConnectionLogEntry> logFileHandler;

    public ConnectionLogHandler(String logDirectoryName, int bufferSize, String clusterName,
                                int queueSize, LogWriter<ConnectionLogEntry> logWriter, boolean useClusterIdInFileName) {
        logFileHandler = new LogFileHandler<>(
                LogFileHandler.Compression.ZSTD,
                bufferSize,
                useClusterIdInFileName ? Text.format("logs/vespa/%s/ConnectionLog.%s.%s", logDirectoryName, clusterName, "%Y%m%d%H%M%S") :
                                          Text.format("logs/vespa/%s/ConnectionLog.%s", logDirectoryName, "%Y%m%d%H%M%S"),
                "0 60 ...",
                useClusterIdInFileName ? Text.format("ConnectionLog.%s", clusterName) :
                                          "ConnectionLog",
                queueSize,
                "connection-logger",
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
