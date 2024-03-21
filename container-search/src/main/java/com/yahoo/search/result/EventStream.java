// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.collections.ListenableArrayList;
import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.processing.Request;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.response.DefaultIncomingData;
import com.yahoo.processing.response.IncomingData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stream of events which can be rendered as Server-Sent Events (SSE).
 *
 * @author lesters
 */
public class EventStream extends Hit implements DataList<Data> {

    private final ListenableArrayList<Data> data = new ListenableArrayList<>(16);
    private final IncomingData<Data> incomingData;
    private final AtomicInteger eventCount = new AtomicInteger(0);

    public final static String EVENT_TYPE_TOKEN = "token";
    public final static String DEFAULT_EVENT_TYPE = EVENT_TYPE_TOKEN;

    public EventStream() {
        super();
        this.incomingData = new DefaultIncomingData<>(this);
    }

    public void add(String data) {
        incoming().add(new Event(eventCount.incrementAndGet(), data, DEFAULT_EVENT_TYPE));
    }

    public void add(String data, String type) {
        incoming().add(new Event(eventCount.incrementAndGet(), data, type));
    }

    public void error(String source, ErrorMessage message) {
        incoming().add(new DefaultErrorHit(source, message));
    }

    public void markComplete() {
        incoming().markComplete();
    }

    @Override
    public Data add(Data event) {
        data.add(event);
        return event;
    }

    @Override
    public Data get(int index) {
        return data.get(index);
    }

    @Override
    public List<Data> asList() {
        return data;
    }

    @Override
    public IncomingData<Data> incoming() {
        return incomingData;
    }

    @Override
    public CompletableFuture<DataList<Data>> completeFuture() {
        return incomingData.completedFuture();
    }

    @Override
    public void addDataListener(Runnable runnable) {
        data.addListener(runnable);
    }

    @Override
    public void close() {
    }

    public static class Event extends ListenableFreezableClass implements Data {

        private final int eventNumber;
        private final String data;
        private final String type;

        public Event(int eventNumber, String data, String type) {
            this.eventNumber = eventNumber;
            this.data = data;
            this.type = type;
        }

        public String toString() {
            return data;
        }

        public String type() {
            return type;
        }

        @Override
        public Request request() {
            return null;
        }

        // For json rendering
        public Hit asHit() {
            Hit hit = new Hit(String.valueOf(eventNumber));
            hit.setField(type, data);
            return hit;
        }

    }

}
