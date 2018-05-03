// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author hakon
 */
public class CursorImpl implements Cursor {
    private final TextBuffer textBuffer;
    private final Object unique = new Object();

    private Position position;

    /**
     * Creates a cursor to a text buffer.
     *
     * WARNING: The text buffer MUST NOT be accessed outside this cursor. This cursor
     * takes sole ownership of the text buffer.
     *
     * @param textBuffer the text buffer this cursor owns and operates on
     */
    CursorImpl(TextBuffer textBuffer) {
        this.textBuffer = textBuffer;
        position = textBuffer.getStartOfText();
    }

    @Override
    public Position getPosition() {
        return position;
    }

    @Override
    public Mark createMark() {
        return new Mark(position, textBuffer.getVersion(), unique);
    }

    @Override
    public String getBufferText() {
        return textBuffer.getString();
    }

    @Override
    public String getLine() {
        return textBuffer.getLine(position);
    }

    @Override
    public String getPrefix() {
        return textBuffer.getLinePrefix(position);
    }

    @Override
    public String getSuffix() {
        return textBuffer.getLineSuffix(position);
    }

    @Override
    public String getTextTo(Mark mark) {
        validateMark(mark);

        Position start = mark.position();
        Position end = position;
        if (start.isAfter(end)) {
            Position tmp = start;
            start = end;
            end = tmp;
        }

        return textBuffer.getSubstring(start, end);
    }

    @Override
    public Cursor moveToStartOfBuffer() {
        position = textBuffer.getStartOfText();
        return this;
    }

    @Override
    public Cursor moveToEndOfBuffer() {
        position = textBuffer.getEndOfText();
        return this;
    }

    @Override
    public Cursor moveToStartOfLine() {
        position = textBuffer.getStartOfLine(position);
        return this;
    }

    @Override
    public Cursor moveToStartOfPreviousLine() {
        position = textBuffer.getStartOfPreviousLine(position);
        return this;
    }

    @Override
    public Cursor moveToStartOfNextLine() {
        position = textBuffer.getStartOfNextLine(position);
        return this;
    }

    @Override
    public Cursor moveToStartOf(int lineIndex) {
        validateLineIndex(lineIndex);
        position = new Position(lineIndex, 0);
        return this;
    }

    @Override
    public Cursor moveToEndOfLine() {
        position = textBuffer.getEndOfLine(position);
        return this;
    }

    @Override
    public Cursor moveToEndOfPreviousLine() {
        return moveToStartOfPreviousLine().moveToEndOfLine();
    }

    @Override
    public Cursor moveToEndOfNextLine() {
        return moveToStartOfNextLine().moveToEndOfLine();
    }

    @Override
    public Cursor moveToEndOf(int lineIndex) {
        return moveToStartOf(lineIndex).moveToEndOfLine();
    }

    @Override
    public Cursor moveForward() {
        return moveForward(1);
    }

    @Override
    public Cursor moveForward(int times) {
        position = textBuffer.forward(position, times);
        return this;
    }

    @Override
    public Cursor moveBackward() {
        return moveBackward(1);
    }

    @Override
    public Cursor moveBackward(int times) {
        position = textBuffer.backward(position, times);
        return this;
    }

    @Override
    public Cursor moveTo(Mark mark) {
        validateMark(mark);
        position = mark.position();
        return this;
    }

    @Override
    public boolean skipBackward(String text) {
        String prefix = getPrefix();
        if (prefix.endsWith(text)) {
            position = new Position(position.lineIndex(), position.columnIndex() - text.length());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean skipForward(String text) {
        String suffix = getSuffix();
        if (suffix.startsWith(text)) {
            position = new Position(position.lineIndex(), position.columnIndex() + text.length());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<Match> moveForwardToStartOfMatch(Pattern pattern) {
        return moveForwardToXOfMatch(pattern, match -> position = match.startOfMatch());
    }

    @Override
    public Optional<Match> moveForwardToEndOfMatch(Pattern pattern) {
        return moveForwardToXOfMatch(pattern, match -> position = match.endOfMatch());
    }

    private Optional<Match> moveForwardToXOfMatch(Pattern pattern, Consumer<Match> callback) {
        Optional<Match> match = textBuffer.findForward(position, pattern);
        match.ifPresent(callback);
        return match;
    }

    @Override
    public Cursor moveTo(Position position) {
        validatePosition(position);
        this.position = position;
        return this;
    }

    @Override
    public Cursor moveTo(int lineIndex, int columnIndex) {
        return moveTo(new Position(lineIndex, columnIndex));
    }

    @Override
    public Cursor write(String text) {
        position = textBuffer.write(position, text);
        return this;
    }

    @Override
    public Cursor writeLine(String line) {
        return write(line).write("\n");
    }

    @Override
    public Cursor writeLines(String... lines) {
        return writeLines(Arrays.asList(lines));
    }

    @Override
    public Cursor writeLines(List<String> lines) {
        return writeLine(String.join("\n", lines));
    }

    @Override
    public Cursor writeNewline() {
        return write("\n");
    }

    @Override
    public Cursor writeNewlineAfter() {
        return writeNewline().moveBackward();
    }

    @Override
    public Cursor deleteAll() {
        moveToStartOfBuffer();
        textBuffer.clear();
        return this;
    }

    @Override
    public Cursor deleteLine() {
        moveToStartOfLine();
        textBuffer.delete(position, textBuffer.getStartOfNextLine(position));
        return this;
    }

    @Override
    public Cursor deletePrefix() {
        Position originalPosition = position;
        moveToStartOfLine();
        textBuffer.delete(position, originalPosition);
        return this;
    }

    @Override
    public Cursor deleteSuffix() {
        textBuffer.delete(position, textBuffer.getEndOfLine(position));
        return this;
    }

    @Override
    public Cursor deleteForward() {
        return deleteForward(1);
    }

    @Override
    public Cursor deleteForward(int times) {
        Position end = textBuffer.forward(position, times);
        textBuffer.delete(position, end);
        return this;
    }

    @Override
    public Cursor deleteBackward() {
        return deleteBackward(1);
    }

    @Override
    public Cursor deleteBackward(int times) {
        Position end = position;
        moveBackward(times);
        textBuffer.delete(position, end);
        return this;
    }

    @Override
    public Cursor deleteTo(Mark mark) {
        Position start = mark.position();
        Position end = position;
        if (start.isAfter(end)) {
            Position tmp = start;
            start = end;
            end = tmp;
        }

        textBuffer.delete(start, end);
        return this;
    }

    @Override
    public boolean replaceMatch(Pattern pattern, Function<Match, String> replacer) {
        Optional<Match> match = moveForwardToStartOfMatch(pattern);
        if (!match.isPresent()) {
            return false;
        }

        // position is unaffected by delete since position == match.get().startOfMatch()
        textBuffer.delete(match.get().startOfMatch(), match.get().endOfMatch());
        write(replacer.apply(match.get()));
        return true;
    }

    @Override
    public int replaceMatches(Pattern pattern, Function<Match, String> replacer) {
        int count = 0;

        for (; replaceMatch(pattern, replacer); ++count) {
            // empty
        }

        return count;
    }

    private void validatePosition(Position position) {
        validateLineIndex(position.lineIndex());

        int maxColumnIndex = textBuffer.getLine(position.lineIndex()).length();
        if (position.columnIndex() < 0 || position.columnIndex() > maxColumnIndex) {
            throw new IndexOutOfBoundsException("Column index of " + position.coordinateString() +
                    " is not in permitted range [0," + maxColumnIndex + "]");
        }
    }

    private void validateLineIndex(int lineIndex) {
        int maxLineIndex = textBuffer.getMaxLineIndex();
        if (lineIndex < 0 || lineIndex > maxLineIndex) {
            throw new IndexOutOfBoundsException("Line index " + lineIndex +
                    " not in permitted range [0," + maxLineIndex + "]");
        }
    }

    private void validateMark(Mark mark) {
        if (mark.secret() != unique) {
            throw new IllegalArgumentException("Unknown mark " + mark);
        }

        if (!mark.version().equals(textBuffer.getVersion())) {
            throw new IllegalArgumentException("Mark " + mark + " is outdated");
        }
    }
}
