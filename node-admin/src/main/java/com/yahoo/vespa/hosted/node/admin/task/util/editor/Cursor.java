// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Simulates an editor cursor.
 *
 * @author hakon
 */
public interface Cursor {
    // CURSOR AND BUFFER QUERIES

    String getBufferText();
    String getLine();
    String getPrefix();
    String getSuffix();
    String getTextTo(Mark mark);

    Position getPosition();
    Mark createMark();

    // CURSOR MOVEMENT

    Cursor moveToStartOfBuffer();
    Cursor moveToEndOfBuffer();

    Cursor moveToStartOfLine();
    Cursor moveToStartOfPreviousLine();
    Cursor moveToStartOfNextLine();
    Cursor moveToStartOf(int lineIndex);

    Cursor moveToEndOfLine();
    Cursor moveToEndOfPreviousLine();
    Cursor moveToEndOfNextLine();
    Cursor moveToEndOf(int lineIndex);

    Cursor moveForward();
    Cursor moveForward(int times);
    Cursor moveBackward();
    Cursor moveBackward(int times);

    Cursor moveTo(Mark mark);
    Cursor moveTo(Position position);
    Cursor moveTo(int lineIndex, int columnIndex);

    Optional<Match> moveForwardToStartOfMatch(Pattern pattern);
    Optional<Match> moveForwardToEndOfMatch(Pattern pattern);

    boolean skipBackward(String text);
    boolean skipForward(String text);

    // BUFFER MODIFICATIONS

    Cursor write(String text);
    Cursor writeLine(String line);
    Cursor writeLines(String... lines);
    Cursor writeLines(List<String> lines);

    Cursor writeNewline();
    Cursor writeNewlineAfter();

    Cursor deleteAll();
    Cursor deleteLine();
    Cursor deletePrefix();
    Cursor deleteSuffix();

    Cursor deleteForward();
    Cursor deleteForward(int times);
    Cursor deleteBackward();
    Cursor deleteBackward(int times);

    Cursor deleteTo(Mark mark);

    boolean replaceMatch(Pattern pattern, Function<Match, String> replacer);

    /**
     * Replace matches of a pattern.
     *
     * <p>The search for {@code pattern} starts at cursor and matches against the remaining line,
     * and the full line for the following lines. Each match is replaced by a String returned by
     * {@code replacer::apply}.
     *
     * <p>The cursor is unchanged without any matches, or moved to the end of the last replacement.
     *
     * <p>To replace all matches in a buffer, first call {@link #moveToStartOfBuffer()} to
     * postion the cursor at the beginning of the buffer.
     *
     * @see #moveForwardToStartOfMatch(Pattern)
     * @see #moveForwardToEndOfMatch(Pattern)
     */
    int replaceMatches(Pattern pattern, Function<Match, String> replacer);
}
