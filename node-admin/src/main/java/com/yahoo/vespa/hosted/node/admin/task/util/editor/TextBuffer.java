// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author hakon
 */
interface TextBuffer {
    // INTERFACE TO IMPLEMENT BY CONCRETE CLASS

    /** Get the version of the buffer - edits increment the version. */
    Version getVersion();

    /** Return the text as a single String (likely) with embedded newlines. */
    String getString();

    /** Return the maximum line index (the minimum line index is 0). */
    int getMaxLineIndex();

    /** @param lineIndex must be in in {@code [0, getMaxLineIndex()]} */
    String getLine(int lineIndex);

    /** Insert the possibly multi-line text at position and return the end position. */
    Position write(Position position, String text);

    /** Delete everything. */
    void clear();

    /** Delete range. */
    void delete(Position start, Position end);

    // DERIVED IMPLEMENTATION

    /**
     * Return the Position closest to {@code position} which is in the range
     * {@code [getStartOfText(), getEndOfText()]}.
     */
    default Position getValidPositionClosestTo(Position position) {
        if (position.isBefore(getStartOfText())) {
            return getStartOfText();
        } else if (position.isAfter(getEndOfText())) {
            return getEndOfText();
        } else {
            return position;
        }
    }

    default String getLine(Position position) { return getLine(position.lineIndex()); }

    default String getLinePrefix(Position position) {
        return getLine(position.lineIndex()).substring(0, position.columnIndex());
    }

    default String getLineSuffix(Position position) {
        return getLine(position.lineIndex()).substring(position.columnIndex());
    }

    default String getSubstring(Position start, Position end) {
        if (start.lineIndex() < end.lineIndex()) {
            StringBuilder builder = new StringBuilder(getLineSuffix(start));
            for (int i = start.lineIndex() + 1; i < end.lineIndex(); ++i) {
                builder.append('\n');
                builder.append(getLine(i));
            }
            return builder.append('\n').append(getLinePrefix(end)).toString();
        } else if (start.lineIndex() == end.lineIndex() && start.columnIndex() <= end.columnIndex()) {
            return getLine(start).substring(start.columnIndex(), end.columnIndex());
        }

        throw new IllegalArgumentException(
                "Bad range [" + start.coordinateString() + "," + end.coordinateString() + ">");
    }

    default Position getStartOfText() { return Position.start(); } // aka (0,0)

    default Position getEndOfText() {
        int maxLineIndex = getMaxLineIndex();
        return new Position(maxLineIndex, getLine(maxLineIndex).length());
    }

    default Position getStartOfLine(Position position) {
        return new Position(position.lineIndex(), 0);
    }

    default Position getEndOfLine(Position position) {
        return new Position(position.lineIndex(), getLine(position).length());
    }

    default Position getStartOfNextLine(Position position) {
        if (position.lineIndex() < getMaxLineIndex()) {
            return new Position(position.lineIndex() + 1, 0);
        } else {
            return getEndOfText();
        }
    }

    default Position getStartOfPreviousLine(Position position) {
        int lineIndex = position.lineIndex();
        if (lineIndex > 0) {
            return new Position(lineIndex - 1, 0);
        } else {
            return getStartOfText();
        }
    }

    default Position forward(Position position, int length) {
        int lineIndex = position.lineIndex();
        int columnIndex = position.columnIndex();

        int offsetLeft = length;
        do {
            String line = getLine(lineIndex);
            int columnIndexWithInfiniteLine = columnIndex + offsetLeft;
            if (columnIndexWithInfiniteLine <= line.length()) {
                return new Position(lineIndex, columnIndexWithInfiniteLine);
            } else if (lineIndex >= getMaxLineIndex()) {
                // End of text
                return new Position(lineIndex, line.length());
            }

            offsetLeft -= line.length() - columnIndex;

            // advance past newline
            --offsetLeft;
            ++lineIndex;
            columnIndex = 0;

            // At this point: offsetLeft is guaranteed to be >= 0, and lineIndex <= max line index
        } while (true);
    }

    default Position backward(Position position, int length) {
        int lineIndex = position.lineIndex();
        int columnIndex = position.columnIndex();

        int offsetLeft = length;
        do {
            int columnIndexWithInfiniteLine = columnIndex - offsetLeft;
            if (columnIndexWithInfiniteLine >= 0) {
                return new Position(lineIndex, columnIndexWithInfiniteLine);
            } else if (lineIndex <= 0) {
                // Start of text
                return new Position(0, 0);
            }

            offsetLeft -= columnIndex;

            // advance past newline
            --offsetLeft;
            --lineIndex;
            columnIndex = getLine(lineIndex).length();

            // At this point: offsetLeft is guaranteed to be <= 0, and lineIndex >= 0
        } while (true);
    }

    default Optional<Match> findForward(Position startPosition, Pattern pattern) {
        for (Position position = startPosition;; position = getStartOfNextLine(position)) {
            String line = getLine(position);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find(position.columnIndex())) {
                return Optional.of(new Match(position.lineIndex(), line, matcher));
            }

            if (position.lineIndex() == getMaxLineIndex()) {
                // search failed - no lines matched
                return Optional.empty();
            }
        }
    }
}
