// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import java.util.LinkedList;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * A filter for log messages that prevents the most recently published messages
 * to be published again. See bug #461290.
 *
 * @author gjoranv
 */
public class RecentLogFilter implements Filter {

    LinkedList<String> recent = new LinkedList<>();
    static final int maxMessages = 6;

    public boolean isLoggable(LogRecord record) {
        String msg = record.getMessage();
        if (msg != null && recent.contains(msg)) {
            return false;  // duplicate
        } else {
            recent.addLast(msg);
            if (recent.size() > maxMessages) {
                recent.removeFirst();
            }
            return true;   // new message
        }
    }
}
