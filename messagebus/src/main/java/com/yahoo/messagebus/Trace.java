// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import java.util.Date;

/**
 * A Trace object contains ad-hoc string notes organized in a strict-loose tree. A Trace object consists of a trace
 * level indicating which trace notes should be included and a TraceTree object containing the tree structure and
 * collecting the trace information. Tracing is used to collect debug information about a Routable traveling through the
 * system. The trace level is in the range [0,9]. 0 means no tracing, and 9 means all tracing is enabled. A client that
 * has the ability to trace information will have a predefined level attached to that information. If the level on the
 * information is lower or equal to the level set in the Trace object, the information will be traced.
 *
 * @author Simon Thoresen Hult
 */
public class Trace {

    private int level = 0;
    private TraceNode root = new TraceNode();

    /**
     * Create an empty trace with level set to 0 (no tracing)
     */
    public Trace() {
        // empty
    }

    /**
     * Create an empty trace with given level.
     *
     * @param level Level to set.
     */
    public Trace(int level) {
        this.level = level;
    }

    /**
     * Remove all trace information and set the trace level to 0.
     *
     * @return This, to allow chaining.
     */
    public Trace clear() {
        level = 0;
        root = new TraceNode();
        return this;
    }

    /**
     * Swap the internals of this with another.
     *
     * @param other The trace to swap internals with.
     * @return This, to allow chaining.
     */
    public Trace swap(Trace other) {
        int level = this.level;
        this.level = other.level;
        other.level = level;

        TraceNode root = this.root;
        this.root = other.root;
        other.root = root;

        return this;
    }

    /**
     * Set the trace level. 0 means no tracing, 9 means enable all tracing.
     *
     * @param level The level to set.
     * @return This, to allow chaining.
     */
    public Trace setLevel(int level) {
        this.level = Math.min(Math.max(level, 0), 9);
        return this;
    }

    /**
     * Returns the trace level.
     *
     * @return The trace level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Check if information with the given level should be traced. This method is added to allow clients to check if
     * something should be traced before spending time building up the trace information itself.
     *
     * @param level The trace level to test.
     * @return True if tracing is enabled for the given level, false otherwise.
     */
    public boolean shouldTrace(int level) {
        return level <= this.level;
    }

    /**
     * Add the given note to the trace information if tracing is enabled for the given level.
     *
     * @param level The trace level of the note.
     * @param note  The note to add.
     * @return True if the note was added to the trace information, false otherwise.
     */
    public boolean trace(int level, String note) {
        return trace(level, note, true);
    }

    /**
     * Add the given note to the trace information if tracing is enabled for the given level. If the addTime parameter
     * is true, then the note is prefixed with the current time. This is the default behaviour when ommiting this
     * parameter.
     *
     * @param level   The trace level of the note.
     * @param note    The note to add.
     * @param addTime Whether or not to prefix note with a timestamp.
     * @return True if the note was added to the trace information, false otherwise.
     */
    public boolean trace(int level, String note, boolean addTime) {
        if (!shouldTrace(level)) {
            return false;
        }
        if (addTime) {
            String timeString = Long.toString(System.currentTimeMillis());
            StringBuilder buf = new StringBuilder();
            buf.append("[");
            int len = timeString.length();
            // something wrong.  handle it by using the input long as a string.
            if (len < 3) {
                buf.append(timeString);
            } else {
                buf.append(timeString.substring(0, len - 3));
                buf.append('.');
                buf.append(timeString.substring(len - 3));
            }
            buf.append("] ");
            buf.append(note);
            root.addChild(buf.toString());
        } else {
            root.addChild(note);
        }
        return true;
    }

    /**
     * Returns the root of the trace tree.
     *
     * @return The root.
     */
    public TraceNode getRoot() {
        return root;
    }

    // Overrides Object.
    @Override
    public String toString() {
        return root.toString(31337);
    }
}
