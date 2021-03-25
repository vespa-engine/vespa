// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author bjorncs
 */
class BlockingQueueRequestLog implements RequestLog {

    private final BlockingQueue<RequestLogEntry> entries = new LinkedBlockingDeque<>();

    @Override public void log(RequestLogEntry entry) { entries.offer(entry); }

    RequestLogEntry poll(Duration timeout) throws InterruptedException {
        return entries.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
