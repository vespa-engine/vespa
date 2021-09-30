// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link ConnectionLog} that aggregates log entries in memory
 *
 * @author bjorncs
 */
class InMemoryConnectionLog implements ConnectionLog {

    private final List<ConnectionLogEntry> logEntries = new CopyOnWriteArrayList<>();

    @Override
    public void log(ConnectionLogEntry entry) {
        logEntries.add(entry);
    }

    List<ConnectionLogEntry> logEntries() { return logEntries; }
}
