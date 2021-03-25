// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;

/**
 * @author mortent
 */
public class FileConnectionLog extends AbstractComponent implements ConnectionLog {

    private final ConnectionLogHandler logHandler;

    @Inject
    public FileConnectionLog(ConnectionLogConfig config) {
        logHandler = new ConnectionLogHandler(config.logDirectoryName(), config.cluster(), config.queueSize(), new JsonConnectionLogWriter());
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