// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import javax.annotation.concurrent.Immutable;
import java.util.Comparator;
import java.util.Objects;

/**
 * Represents a position in the buffer
 *
 * @author hakon
 */
@Immutable
public class Position implements Comparable<Position> {
    private static final Position START_POSITION = new Position(0, 0);

    private static final Comparator<Position> COMPARATOR = Comparator
            .comparingInt((Position position) -> position.lineIndex())
            .thenComparingInt((Position position) -> position.columnIndex());

    private final int lineIndex;
    private final int columnIndex;

    /** Returns the first position at line index 0 and column index 0 */
    public static Position start() {
        return START_POSITION;
    }

    Position(int lineIndex, int columnIndex) {
        this.lineIndex = lineIndex;
        this.columnIndex = columnIndex;
    }

    public int lineIndex() {
        return lineIndex;
    }

    public int columnIndex() {
        return columnIndex;
    }

    @Override
    public int compareTo(Position that) {
        return COMPARATOR.compare(this, that);
    }

    public boolean isAfter(Position that) { return compareTo(that) > 0; }
    public boolean isNotAfter(Position that) { return !isAfter(that); }
    public boolean isBefore(Position that) { return compareTo(that) < 0; }
    public boolean isNotBefore(Position that) { return !isBefore(that); }

    public String coordinateString() {
        return "(" + lineIndex + "," + columnIndex + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return lineIndex == position.lineIndex &&
                columnIndex == position.columnIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineIndex, columnIndex);
    }

    @Override
    public String toString() {
        return coordinateString();
    }
}
