// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 *
 */

package com.yahoo.logserver.formatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.yahoo.log.LogMessage;

/**
 * Creates human-readable text representation of log message.
 *
 * @author Bjorn Borud
 */
public class TextFormatter implements LogFormatter {
    static final SimpleDateFormat dateFormat;

    static {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public String format(LogMessage msg) {
        StringBuffer sbuf = new StringBuffer(150);
        sbuf.append(dateFormat.format(new Date(msg.getTime())))
            .append(" ")
            .append(msg.getHost())
            .append(" ")
            .append(msg.getThreadProcess())
            .append(" ")
            .append(msg.getService())
            .append(" ")
            .append(msg.getComponent())
            .append(" ")
            .append(msg.getLevel().toString())
            .append(" ")
            .append(msg.getPayload())
            .append("\n");

        return sbuf.toString();
    }

    public String description() {
        return "Format log-message as human readable text";
    }
}
