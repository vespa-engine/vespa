// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.prelude.query.WeightedSetItem;

import java.util.Arrays;

/**
 * Parser of parameter lists on the form {key:value, key:value} or [[key,value], [key,value], ...]
 *
 * @author bratseth
 */
class ParameterListParser {

    public static void addItemsFromString(String string, WeightedSetItem out) {
        var s = new ParsableString(string);
        switch (s.peek()) {
            case '[' : addArrayItems(s, out); break;
            case '{' : addMapItems(s, out); break;
            default : throw new IllegalArgumentException("Expected a string starting by '[' or '{', " +
                                                         "but was '" + s.peek() + "'");
        }
    }

    private static void addArrayItems(ParsableString s, WeightedSetItem out) {
        s.pass('[');
        while (s.peek() != ']') {
            s.pass('[');
            long key = s.longTo(s.position(','));
            s.pass(',');
            int value = s.intTo(s.position(']'));
            s.pass(']');
            out.addToken(key, value);
            s.passOptional(',');
            if (s.atEnd()) throw new IllegalArgumentException("Expected an array ending by ']'");
        }
        s.pass(']');
    }

    private static void addMapItems(ParsableString s, WeightedSetItem out) {
        s.pass('{');
        while (s.peek() != '}') {
            String key;
            if (s.passOptional('\'')) {
                key = s.stringTo(s.position('\''));
                s.pass('\'');
            }
            else if (s.passOptional('"')) {
                key = s.stringTo(s.position('"'));
                s.pass('"');
            }
            else {
                key = s.stringTo(s.position(':')).trim();
            }
            s.pass(':');
            int value = s.intTo(s.position(',','}'));
            out.addToken(key, value);
            s.passOptional(',');
            if (s.atEnd()) throw new IllegalArgumentException("Expected a map ending by '}'");
        }
        s.pass('}');
    }

    private static class ParsableString {

        int position = 0;
        String s;

        ParsableString(String s) {
            this.s = s;
        }

        /**
         * Returns the next non-space character or UNASSIGNED if we have reached the end of the string.
         * The current position is not changed.
         */
        char peek() {
            int localPosition = position;
            while (localPosition < s.length()) {
                char nextChar = s.charAt(localPosition++);
                if (!Character.isSpaceChar(nextChar))
                    return nextChar;
            }
            return Character.UNASSIGNED;
        }

        /**
         * Verifies that the next non-space character is the given and moves the position past it.
         *
         * @throws IllegalArgumentException if the next non-space character is not the given character
         */
        void pass(char character) {
            while (position < s.length()) {
                char nextChar = s.charAt(position++);
                if (!Character.isSpaceChar(nextChar)) {
                    if (nextChar == character)
                        return;
                    else
                        throw new IllegalArgumentException("Expected '" + character + "' at position " + (position-1) +
                                                           " but got '" + nextChar + "'");
                }
            }
            throw new IllegalArgumentException("Expected '" + character + "' at position " + (position-1) +
                                               " but reached the end");
        }

        /**
         * Checks if the next non-space character is the given and moves the position past it if so.
         * Does not change the position otherwise.
         *
         * @return true if the next non-space character was the given character
         */
        boolean passOptional(char character) {
            int localPosition = position;
            while (localPosition < s.length()) {
                char nextChar = s.charAt(localPosition++);
                if (!Character.isSpaceChar(nextChar)) {
                    if (nextChar == character) {
                        position = localPosition;
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            return false;
        }

        /**
         * Returns the position of the next occurrence of any of the given characters.
         *
         * @throws IllegalArgumentException if there are no further occurrences of any of the given characters
         */
        int position(char ... characters) {
            int localPosition = position;
            while (localPosition < s.length()) {
                char nextChar = s.charAt(localPosition);
                for (char character : characters)
                    if (nextChar == character) return localPosition;
                localPosition++;
            }
            throw new IllegalArgumentException("Expected one of " + Arrays.toString(characters) + " after " + position);
        }

        boolean atEnd() {
            return position >= s.length();
        }

        /**
         * Returns the string value from the current to the given position, and moves the current
         * position to the next character.
         *
         * @throws IllegalArgumentException if end is beyond the last position of the string
         */
        String stringTo(int end) {
            try {
                String value = s.substring(position, end);
                position = end;
                return value;
            }
            catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException(end + " is larger than the size of the string,  " + s.length());
            }
        }

        /**
         * Returns the int value from the current to the given position, and moves the current
         * position to the next character.
         *
         * @throws IllegalArgumentException if the string cannot be parsed to an int or end is larger than the string
         */
        int intTo(int end) {
            int start = position;
            String value = stringTo(end);
            try {
                return Integer.parseInt(value.trim());
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected an integer between positions " + start + " and " + end +
                                                   ", but got " + value);
            }
        }

        /**
         * Returns the long value from the current to the given position, and moves the current
         * position to the next character.
         *
         * @throws IllegalArgumentException if the string cannot be parsed to a long or end is larger than the string
         */
        long longTo(int end) {
            int start = position;
            String value = stringTo(end);
            try {
                return Long.parseLong(value.trim());
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected an integer between positions " + start + " and " + end +
                                                   ", but got " + value);
            }
        }

    }

}
