// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author hakon
 */
public class TextUtil {
    private TextUtil() {}

    /**
     * Splits {@code text} by newline.
     *
     * @param text
     * @param prune remove the last line if it is empty. Examples:
     *              {@code "" -> []},
     *              {@code "foo\n" -> "foo"},
     *              {@code "foo\n\n" -> ["foo", ""]}. If false, these would return
     *              {@code [""]},
     *              {@code ["foo", ""]}, and
     *              {@code ["foo", "", ""]}, respectively.
     * @see #splitString(String, boolean, boolean)
     */
    public static List<String> splitString(String text, boolean prune) {
        return splitString(text, prune, prune);
    }

    /**
     * Splits {@code text} by newline (LF {@code '\n'}).
     *
     * @param text the text to split into lines
     * @param empty whether an empty text implies an empty List (true), or a List with one
     *              empty String element (false)
     * @param prune whether a text ending with a newline will result in a List ending with the
     *              preceding line (true), or to add an empty String element (false)
     */
    public static List<String> splitString(String text, boolean empty, boolean prune) {
        List<String> lines = new ArrayList<>();
        splitString(text, empty, prune, lines::add);
        return lines;
    }

    /**
     * Splits text by newline, passing each line to a consumer.
     *
     * @see #splitString(String, boolean, boolean)
     */
    public static void splitString(String text,
                                   boolean empty,
                                   boolean prune,
                                   Consumer<String> consumer) {
        if (text.isEmpty()) {
            if (!empty) {
                consumer.accept(text);
            }
            return;
        }

        final int endIndex = text.length();

        int start = 0;
        for (int end = text.indexOf('\n');
             end != -1;
             start = end + 1, end = text.indexOf('\n', start)) {
            consumer.accept(text.substring(start, end));
        }

        if (start < endIndex || !prune) {
            consumer.accept(text.substring(start));
        }
    }
}
