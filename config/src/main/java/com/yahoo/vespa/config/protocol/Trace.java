// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.slime.*;
import com.yahoo.text.Utf8;
import com.yahoo.yolean.trace.TraceNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;

/**
 * A trace utility that can serialize/deserialize to/from {@link Slime}
 *
 * @author Ulf Lilleengen
 */
public class Trace {
    private static final String TRACE_TRACELOG = "traceLog";
    private static final String TRACE_TRACELEVEL = "traceLevel";
    private final int traceLevel;
    private final TraceNode traceNode;
    private final Clock clock;

    private Trace(int traceLevel, TraceNode traceNode, Clock clock) {
        this.traceLevel = traceLevel;
        this.traceNode = traceNode;
        this.clock = clock;
    }


    public void trace(int level, String message) {
        if (shouldTrace(level)) {
            addTrace(message);
        }
    }

    private void addTrace(String message) {
        traceNode.add(new TraceNode(message, clock.millis()));
    }

    public static Trace createNew(int traceLevel, Clock clock) {
        return new Trace(traceLevel, new TraceNode(null, clock.millis()), clock);
    }

    public static Trace createNew(int traceLevel) {
        return createNew(traceLevel, Clock.systemUTC());
    }

    public static Trace fromSlime(Inspector inspector) {
        int traceLevel = deserializeTraceLevel(inspector);
        Clock clock = Clock.systemUTC();
        SlimeTraceDeserializer deserializer = new SlimeTraceDeserializer(inspector.field(TRACE_TRACELOG));
        return new Trace(traceLevel, deserializer.deserialize(), clock);
    }

    private static int deserializeTraceLevel(Inspector inspector) {
        return (int) inspector.field(TRACE_TRACELEVEL).asLong();
    }

    public void serialize(Cursor cursor) {
        cursor.setLong(TRACE_TRACELEVEL, traceLevel);
        SlimeTraceSerializer serializer = new SlimeTraceSerializer(cursor.setObject(TRACE_TRACELOG));
        traceNode.accept(serializer);
    }

    public static Trace createDummy() {
        return Trace.createNew(0);
    }

    public int getTraceLevel() {
        return traceLevel;
    }

    public boolean shouldTrace(int level) {
        return level <= traceLevel;
    }

    public String toString(boolean compact) {
        Slime slime = new Slime();
        serialize(slime.setObject());
        JsonFormat format = new JsonFormat(compact);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            format.encode(baos, slime);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to encode trace as JSON", e);
        }
        return Utf8.toString(baos.toByteArray());
    }


    @Override
    public String toString() {
        return toString(false);
    }

    private final static int systemTraceLevel = Integer.getInteger("config.protocol.traceLevel", 0);
    public static Trace createNew() {
        return createNew(systemTraceLevel);
    }
}
