// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.container.logging.ConnectionLogConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author mortent
 */
public class FileConnectionLog implements ConnectionLog {

    private static final Logger logger = Logger.getLogger(FileConnectionLog.class.getName());
    private final ConnectionLogHandler logHandler;

    @Inject
    public FileConnectionLog(ConnectionLogConfig config) {
        logHandler = new ConnectionLogHandler(config.cluster());
    }

    @Override
    public void log(ConnectionLogEntry connectionLogEntry) {
        try {
            logHandler.connection.log(Level.INFO, connectionLogEntry.toJson());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to write connection log entry for connection id " + connectionLogEntry.id(), e);
        }
    }
}
