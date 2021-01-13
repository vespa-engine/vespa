// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author mortent
 */
public class SimpleConnectionLog implements ConnectionLog {

    private static final Logger logger = Logger.getLogger(SimpleConnectionLog.class.getName());

    @Override
    public void log(ConnectionLogEntry connectionLogEntry) {
        try {
            System.out.println(connectionLogEntry.toJson());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to write connection log entry for connection id " + connectionLogEntry.id(), e);
        }
    }
}
