// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A log formatter that returns a plain log message only with level, not
 * including timestamp and method (as java.util.logging.SimpleFormatter).
 * See bug #1789867.
 *
 * @author gjoranv
 */
public class PlainFormatter extends Formatter {

    public PlainFormatter() {
        super();
    }

    public String format(LogRecord record) {
        StringBuffer sb = new StringBuffer();

        sb.append(record.getLevel().getName()).append(": ");
        sb.append(formatMessage(record)).append("\n");

        return sb.toString();
    }
}
