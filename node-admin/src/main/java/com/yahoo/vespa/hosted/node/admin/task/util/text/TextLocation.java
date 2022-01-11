// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.text;

/**
 * The location within an implied multi-line String.
 *
 * @author hakonhall
 */
//@Immutable
public class TextLocation {
    private final int offset;
    private final int lineIndex;
    private final int columnIndex;

    public TextLocation() { this(0, 0, 0); }

    public TextLocation(int offset, int lineIndex, int columnIndex) {
        this.offset = offset;
        this.lineIndex = lineIndex;
        this.columnIndex = columnIndex;
    }

    public int offset() { return offset; }
    public int lineIndex() { return lineIndex; }
    public int line() { return lineIndex + 1; }
    public int columnIndex() { return columnIndex; }
    public int column() { return columnIndex + 1; }

    public String lineAndColumnText() { return "line " + line() + " and column " + column(); }
}
