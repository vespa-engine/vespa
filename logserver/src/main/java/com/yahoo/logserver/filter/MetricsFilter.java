// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter;

import com.yahoo.log.LogLevel;
import com.yahoo.log.event.Count;
import com.yahoo.log.event.CountGroup;
import com.yahoo.log.event.Event;
import com.yahoo.log.event.Histogram;
import com.yahoo.log.event.MalformedEventException;
import com.yahoo.log.event.Value;
import com.yahoo.log.event.ValueGroup;
import com.yahoo.log.LogMessage;

/**
 * This filter matches events that are used for monitoring, specificly
 * the Count and Value events.
 *
 * @author Bjorn Borud
 */
public class MetricsFilter implements LogFilter {
    public boolean isLoggable (LogMessage msg) {
        if (msg.getLevel() != LogLevel.EVENT) {
            return false;
        }

        Event event;
        try {
            event = msg.getEvent();
        }
        catch (MalformedEventException e) {
            return false;
        }

        // if it is not Count, Value or something which will generate
        // Count or Value we don't care
        if (! ((event instanceof Count)
               || (event instanceof Value)
               || (event instanceof Histogram)
               || (event instanceof CountGroup)
               || (event instanceof ValueGroup))) {
            return false;
        }

        return true;
    }

    public String description () {
        return "Match all events representing system metrics (Counts, Values, etc).";
    }
}
