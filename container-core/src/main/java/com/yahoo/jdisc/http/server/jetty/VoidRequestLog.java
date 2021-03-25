// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;

/**
 * @author bjorncs
 */
public class VoidRequestLog implements RequestLog {

    @Override public void log(RequestLogEntry entry) {}

}
