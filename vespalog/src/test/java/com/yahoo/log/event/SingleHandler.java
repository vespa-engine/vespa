// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class SingleHandler extends Handler {
    private LogRecord lastRecord;

    public void flush() {}
    public void close() {}

    public void publish (LogRecord r) {
        lastRecord = r;
    }

    public LogRecord lastRecord () {
        return lastRecord;
    }
}
