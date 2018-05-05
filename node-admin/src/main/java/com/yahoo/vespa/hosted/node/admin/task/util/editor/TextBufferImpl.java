// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.LinkedList;
import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.task.util.editor.TextUtil.splitString;

/**
 * @author hakon
 */
public class TextBufferImpl implements TextBuffer {
    /** Invariant: {@code size() >= 1}. An empty text buffer {@code => [""]} */
    private final LinkedList<String> lines = new LinkedList<>();

    private Version version = new Version();

    TextBufferImpl() {
        lines.add("");
    }

    TextBufferImpl(String text) {
        this();
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
        List<String> linesToInsert = new LinkedList<>(splitString(text, true, false));
        if (linesToInsert.isEmpty()) {
            return position;
        }

        // The position splits that line in two, and both prefix and suffix must be preserved
        linesToInsert.set(0, getLinePrefix(position) + linesToInsert.get(0));
        String lastLine = linesToInsert.get(linesToInsert.size() - 1);
        int endColumnIndex = lastLine.length();
        linesToInsert.set(linesToInsert.size() - 1, lastLine + getLineSuffix(position));

        // Set the first line at lineIndex, insert the rest.
        int lineIndex = position.lineIndex();
        int endLineIndex = lineIndex + linesToInsert.size() - 1;
        lines.set(lineIndex, linesToInsert.remove(0));
        lines.addAll(lineIndex + 1, linesToInsert);

        incrementVersion();

        return new Position(endLineIndex, endColumnIndex);
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
        for (int i = startIndex; i < endIndex; ++i) {
            lines.remove(startIndex);
        }
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
