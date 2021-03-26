// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

/**
 * Workaround for asynchronous shutdown of Jetty during unit tests.
 *
 * @author jonmv
 */
abstract class BlockingLog<LogEntry> {

    private final BlockingQueue<LogEntry> entries = new LinkedBlockingQueue<>();

    public void log(LogEntry entry) { entries.add(entry); }

    LogEntry take() {
        try {
            LogEntry entry = entries.poll(5, TimeUnit.SECONDS);
            assertNotNull("No log entry available within 5 seconds", entry);
            return entry;
        }
        catch (InterruptedException e) {
            throw new AssertionError("Interrupted waiting for log entries", e);
        }
    }

}

class BlockingRequestLog extends BlockingLog<RequestLogEntry> implements RequestLog { }

class BlockingConnectionLog extends BlockingLog<ConnectionLogEntry> implements ConnectionLog { }
