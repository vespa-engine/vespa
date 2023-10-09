// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.slime.Slime;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class TraceTest {

    @Test
    public void trace_level_limits_tracing() {
        Trace trace = Trace.createNew(3);
        assertTrue(trace.shouldTrace(0));
        assertTrue(trace.shouldTrace(1));
        assertTrue(trace.shouldTrace(2));
        assertTrue(trace.shouldTrace(3));
        assertFalse(trace.shouldTrace(4));
        assertFalse(trace.shouldTrace(5));
        trace.trace(1, "foo");
        trace.trace(1, "foo2");
        trace.trace(2, "bar");
        trace.trace(3, "baz");
        trace.trace(3, "baz2");
        trace.trace(4, "quux");
        trace.trace(5, "quux2");
        String str = trace.toString();
        assertTrue(str.contains("foo"));
        assertTrue(str.contains("foo2"));
        assertTrue(str.contains("bar"));
        assertTrue(str.contains("baz"));
        assertTrue(str.contains("baz2"));
        assertFalse(str.contains("quux"));
        assertFalse(str.contains("quux2"));
    }

    @Test
    public void trace_serialization() {
        Trace trace = Trace.createNew(1);
        trace.trace(0, "foobar");
        trace.trace(1, "barbaz");
        Slime slime = new Slime();
        trace.serialize(slime.setObject());
        Trace trace2 = Trace.fromSlime(slime.get());
        trace2.trace(1, "quux");
        String trace1Str = trace.toString();
        String trace2Str = trace2.toString();
        assertTrue(trace1Str.contains("foobar"));
        assertTrue(trace1Str.contains("barbaz"));
        assertFalse(trace1Str.contains("quux"));

        assertTrue(trace2Str.contains("foobar"));
        assertTrue(trace2Str.contains("barbaz"));
        assertTrue(trace2Str.contains("quux"));
    }

}
