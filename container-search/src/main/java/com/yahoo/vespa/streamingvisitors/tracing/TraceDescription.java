// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import com.yahoo.messagebus.Trace;

/**
 * High-level description of a trace and the actual trace itself. Used to provide more
 * information to logs etc than just the trace tree. The description will usually contain
 * context for the trace, such as the orginal query string, desired timeout etc.
 */
public class TraceDescription {

    private final Trace trace;
    private final String description;

    public TraceDescription(Trace trace, String description) {
        this.trace = trace;
        this.description = description;
    }

    public Trace getTrace() {
        return trace;
    }

    public String getDescription() {
        return description;
    }

}
