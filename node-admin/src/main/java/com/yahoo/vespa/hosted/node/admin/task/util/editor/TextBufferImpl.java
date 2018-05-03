// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.LinkedList;
import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.task.util.editor.TextUtil.splitString;

/**
 * @author hakon
 */
public class TextBufferImpl implements TextBuffer {
    private final LinkedList<String> lines = new LinkedList<>();

    private Version version = new Version();

    TextBufferImpl() {
        // Invariant about always size() >= 0, and an empty text buffer => [""]
        lines.add("");
    }

    TextBufferImpl(String text) {
        lines.add("");
        write(getStartOfText(), text);
        // reset version
        version = new Version();
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public String getString() {
        return String.join("\n", lines);
    }

    @Override
    public int getMaxLineIndex() {
        return lines.size() - 1;
    }

    @Override
    public String getLine(int lineIndex) {
        return lines.get(lineIndex);
    }

    @Override
    public Position write(Position position, String text) {
        List<String> linesToInsert = splitString(text, true, false);
        if (linesToInsert.isEmpty()) {
            return position;
        }

        int lineIndex = position.lineIndex();

        String prefix = getLinePrefix(position);
        linesToInsert.set(0, prefix + linesToInsert.get(0));

        // Insert the current prefix to the first line
        int numberOfLinesToInsert = linesToInsert.size() - 1; // 1 will be set, the rest inserted
        String prefixOfCursorAfterwards = linesToInsert.get(numberOfLinesToInsert);

        // Append the current suffix to the last line
        String suffix = getLineSuffix(position);
        String lastLine = prefixOfCursorAfterwards + suffix;
        linesToInsert.set(numberOfLinesToInsert, lastLine);

        // The first line is overwritten with set()
        lines.set(lineIndex, linesToInsert.remove(0));

        // The following lines must be inserted
        lines.addAll(lineIndex + 1, linesToInsert);

        incrementVersion();

        return new Position(lineIndex + linesToInsert.size(), prefixOfCursorAfterwards.length());
    }

    @Override
    public void clear() {
        lines.clear();
        lines.add("");
    }

    @Override
    public void delete(Position start, Position end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start position " + start +
                    " is after end position " + end);
        }

        String prefix = getLinePrefix(start);
        String suffix = getLineSuffix(end);
        String stichedLine = prefix + suffix;

        lines.set(start.lineIndex(), stichedLine);

        deleteLines(start.lineIndex() + 1, end.lineIndex() + 1);

        incrementVersion();
    }

    private void deleteLines(int startIndex, int endIndex) {
        int fromIndex = endIndex;
        for (int toIndex = startIndex; fromIndex <= getMaxLineIndex(); ++toIndex, ++fromIndex) {
            lines.set(toIndex, lines.get(fromIndex));
        }

        truncate(getMaxLineIndex() - (endIndex - startIndex));
    }

    private void truncate(int newMaxLineIndex) {
        while (getMaxLineIndex() > newMaxLineIndex) {
            lines.remove(getMaxLineIndex());
        }
    }

    private void incrementVersion() {
        version = version.next();
    }
}
