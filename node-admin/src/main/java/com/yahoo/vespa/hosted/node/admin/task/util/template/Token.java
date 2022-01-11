// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.Optional;

/**
 * @author hakonhall
 */
class Token {
    static final char NEGATE_CHAR = '!';
    static final char REMOVE_NEWLINE_CHAR = '-';
    static final char VARIABLE_DIRECTIVE_CHAR = '=';

    static Optional<String> skipId(Cursor cursor) {
        if (cursor.eot() || !isIdStart(cursor.getChar())) return Optional.empty();

        Cursor start = new Cursor(cursor);
        cursor.increment();

        while (!cursor.eot() && isIdPart(cursor.getChar()))
            cursor.increment();

        return Optional.of(new CursorRange(start, cursor).string());
    }

    /** A delimiter either starts a directive (e.g. %{) or ends it (e.g. }). */
    static String verifyDelimiter(String delimiter) {
        if (!isAsciiToken(delimiter)) {
            throw new IllegalArgumentException("Invalid delimiter: '" + delimiter + "'");
        }
        return delimiter;
    }

    /** Returns true for a non-empty string with only ASCII token characters. */
    private static boolean isAsciiToken(String string) {
        if (string.isEmpty()) return false;
        for (char c : string.toCharArray()) {
            if (!isAsciiTokenChar(c)) return false;
        }
        return true;
    }

    /** Returns true if char is a printable ASCII character except space (isgraph(3)). */
    private static boolean isAsciiTokenChar(char c) {
        // 0x1F unit separator
        // 0x20 space
        // 0x21 !
        // ...
        // 0x7E ~
        // 0x7F del
        return 0x20 < c && c < 0x7F;
    }

    // Our identifiers are equivalent to a Java identifiers.
    private static boolean isIdStart(char c) { return Character.isJavaIdentifierStart(c); }
    private static boolean isIdPart(char c) { return Character.isJavaIdentifierPart(c); }
}
