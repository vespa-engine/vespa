// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;

import java.util.logging.Logger;

/**
 * @author mortent
 */
public class FileConnectionLog extends AbstractComponent implements ConnectionLog {

    private static final Logger logger = Logger.getLogger(FileConnectionLog.class.getName());
    private final ConnectionLogHandler logHandler;

    @Inject
    public FileConnectionLog(ConnectionLogConfig config) {
        logHandler = new ConnectionLogHandler(config.cluster(), new JsonConnectionLogWriter());
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