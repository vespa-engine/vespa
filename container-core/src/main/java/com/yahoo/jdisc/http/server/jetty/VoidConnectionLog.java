// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;

/**
 * @author mortent
 */
public class VoidConnectionLog implements ConnectionLog {

    @Override
    public void log(ConnectionLogEntry connectionLogEntry) {
    }
}
