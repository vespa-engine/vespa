// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.result;

import com.yahoo.processing.response.DefaultIncomingData;

/**
 * A stream of events which can be rendered as Server-Sent Events (SSE).
 *
 * @author lesters
 */
public class EventStream extends HitGroup {

    private int eventCount = 0;

    public static final String DEFAULT_EVENT_TYPE = "token";

    private EventStream(String id, DefaultIncomingData<Hit> incomingData) {
        super(id, new Relevance(1), incomingData);
        this.setOrdered(true);  // avoid hit group ordering - important as sequence as inserted should be kept
    }

    public static EventStream create(String id) {
        DefaultIncomingData<Hit> incomingData = new DefaultIncomingData<>();
        EventStream stream = new EventStream(id, incomingData);
        incomingData.assignOwner(stream);
        return stream;
    }

    public void add(String data) {
        add(data, DEFAULT_EVENT_TYPE);
    }

    public void add(String data, String type) {
        incoming().add(new Event(String.valueOf(eventCount + 1), data, type));
        eventCount++;
    }

    public void error(String source, ErrorMessage message) {
        incoming().add(new DefaultErrorHit(source, message));
    }

    public void markComplete() {
        incoming().markComplete();
    }

    public static class Event extends Hit {

        private final String type;

        public Event(String id, String data, String type) {
            super(id);
            this.type = type;
            setField(type, data);
        }

        public String toString() {
            return getField(type).toString();
        }

        public String type() {
            return type;
        }

    }
}
