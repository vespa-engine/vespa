// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;

/**
 * @author mortent
 */
public class FileConnectionLog extends AbstractComponent implements ConnectionLog {

    private final ConnectionLogHandler logHandler;

    @Inject
    public FileConnectionLog(ConnectionLogConfig config) {
        logHandler = new ConnectionLogHandler(config.logDirectoryName(), config.bufferSize(), config.cluster(),
                queueSize(config), new JsonConnectionLogWriter(), config.useClusterIdInFileName());
    }

    private static int queueSize(ConnectionLogConfig config) {
        if (config.queueSize() != -1) return config.queueSize();
        return Math.max(4096, Runtime.getRuntime().availableProcessors() * 512);
    }

    @Override
    public void log(ConnectionLogEntry connectionLogEntry) {
        logHandler.log(connectionLogEntry);
    }

    @Override
    public void deconstruct() {
        logHandler.shutdown();
    }

}