// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.log.event.Event;

/**
 * @author thomasg
 */
public class VespaLogEventLogger implements EventLogger {
    public void value(String name, double value) {
        Event.value(name, value);
    }

    public void count(String name, long value) {
        Event.count(name, value);
    }
}
