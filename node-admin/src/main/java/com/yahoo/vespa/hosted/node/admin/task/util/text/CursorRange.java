// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.text;

/**
 * A start- and end- offset in an underlying String.
 *
 * @author hakonhall
 */
public class CursorRange {
    private final Cursor start;
    private final Cursor end;

    @SuppressWarnings("StringEquality")
    public CursorRange(Cursor start, Cursor end) {
        if (start.fullText() != end.fullText()) {
            throw new IllegalArgumentException("start and end points to different texts");
        }

        if (start.offset() > end.offset()) {
            throw new IllegalArgumentException("start offset " + start.offset() +
                                               " is beyond end offset " + end.offset());
        }

        this.start = new Cursor(start);
        this.end = new Cursor(end);
    }

    public CursorRange(CursorRange that) {
        this.start = new Cursor(that.start);
        this.end = new Cursor(that.end);
    }

    public Cursor start() { return new Cursor(start); }
    public Cursor end() { return new Cursor(end); }
    public int length() { return end.offset() - start.offset(); }
    public String string() { return start.fullText().substring(start.offset(), end.offset()); }
    public void appendTo(StringBuilder buffer) { buffer.append(start.fullText(), start.offset(), end.offset()); }
}
