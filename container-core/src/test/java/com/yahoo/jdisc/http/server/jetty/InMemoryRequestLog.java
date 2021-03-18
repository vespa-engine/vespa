// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author bjorncs
 */
public class InMemoryRequestLog implements RequestLog {

    private final List<RequestLogEntry> entries = new CopyOnWriteArrayList<>();

    @Override public void log(RequestLogEntry entry) { entries.add(entry); }

    List<RequestLogEntry> entries() { return entries; }
}
