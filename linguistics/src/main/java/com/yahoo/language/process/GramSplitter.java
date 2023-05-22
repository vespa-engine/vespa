// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A class which splits consecutive word character sequences into overlapping character n-grams.
 * For example "en gul bille sang" split into 2-grams becomes
 * "en gu ul bi il ll le sa an ng", and split into 3-grams becomes "en gul bil ill lle san ang".
 * <p>
 * This class is multithread safe.
 *
 * @author bratseth
 */
public class GramSplitter {

    private final CharacterClasses characterClasses;

    public GramSplitter(CharacterClasses characterClasses) {
        this.characterClasses = characterClasses;
    }

    /**
     * Splits the input into grams of size n and returns an iterator over grams represented as [start index,length]
     * pairs into the input string.
     * <p>
     * The iterator is implemented as a sliding view over the input string rather than being backed by a
     * list, which makes this space efficient for large strings.
     *
     * @param input the input string to be split, cannot be null
     * @param n     the gram size, a positive integer
     * @return a read only iterator over the resulting grams
     * @throws NullPointerException     if input==null
     * @throws IllegalArgumentException if n is less than 1
     */
    public GramSplitterIterator split(String input, int n) {
        if (input == null) throw new NullPointerException("input cannot be null");
        if (n < 1) throw new IllegalArgumentException("n (gram size) cannot be smaller than 1, was " + n);
        return new GramSplitterIterator(input, n, characterClasses);
    }

    public static class GramSplitterIterator implements Iterator<Gram> {

        private final CharacterClasses characterClasses;

        /** Text to split */
        private final UnicodeString input;

        /** Gram size in code points */
        private final int n;

        /** Current position in the string */
        private int i = 0;

        /** Whether the last thing that happened was being on a separator (including the start of the string) */
        private boolean isFirstAfterSeparator = true;

        /** The next gram or null if not determined yet */
        private Gram nextGram = null;

        public GramSplitterIterator(String input, int n, CharacterClasses characterClasses) {
            this.input = new UnicodeString(input);
            this.n = n;
            this.characterClasses = characterClasses;
        }

        @Override
        public boolean hasNext() {
            if (nextGram != null) return true;
            nextGram = findNext();
            return nextGram != null;
        }

        @Override
        public Gram next() {
            Gram currentGram = nextGram;
            if (currentGram == null)
                currentGram = findNext();
            if (currentGram == null)
                throw new NoSuchElementException("No next gram at position " + i);
            nextGram = null;
            return currentGram;
        }

        private Gram findNext() {
            // Skip to next indexable character
            while (i < input.length() && !isIndexable(input.codePointAt(i))) {
                i = input.next(i);
                isFirstAfterSeparator = true;
            }
            if (i >= input.length()) return null; // no indexable characters

            int tokenStart = i;
            UnicodeString gram = input.substring(tokenStart, n);
            int tokenEnd = tokenEnd(gram);
            gram = new UnicodeString(gram.toString().substring(0, tokenEnd));
            if (gram.codePointCount() == n) { // normal case: got a full length gram
                Gram g = new Gram(i, gram.codePointCount());
                i = input.next(i);
                isFirstAfterSeparator = false;
                return g;
            }
            else { // gram is too short due either to being a symbol, being followed by a non-word separator, or end of string
                if (isFirstAfterSeparator || ( gram.codePointCount() == 1 && characterClasses.isSymbol(gram.codePointAt(0)))) { // make a gram anyway
                    Gram g = new Gram(i, gram.codePointCount());
                    i = input.next(i);
                    isFirstAfterSeparator = false;
                    return g;
                } else { // skip to next
                    i = input.skip(gram.codePointCount(), i);
                    isFirstAfterSeparator = true;
                    return findNext();
                }
            }
        }

        private boolean isIndexable(int codepoint) {
            if (characterClasses.isLetterOrDigit(codepoint)) return true;
            if (characterClasses.isSymbol(codepoint)) return true;
            return false;
        }

        /** Given a string s starting by an indexable character, return the position where that token should end. */
        private int tokenEnd(UnicodeString s) {
            if (characterClasses.isSymbol(s.codePointAt(0)))
                return s.next(0); // symbols have length 1

            int i = 0;
            for (; i < s.length(); i = s.next(i)) {
                if ( ! characterClasses.isLetterOrDigit(s.codePointAt(i)))
                    return i;
            }
            return i;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("This iterator is read only");
        }

        /**
         * Convenience list which splits the remaining items in this iterator into a list of gram strings
         *
         * @return an immutable list of extracted grams
         */
        public List<String> toExtractedList() {
            List<String> gramList = new ArrayList<>();
            while (hasNext())
                gramList.add(next().extractFrom(input));
            return Collections.unmodifiableList(gramList);
        }
    }

    /**
     * An immutable start index and length pair
     */
    public static final class Gram {

        private final int start, codePointCount;

        public Gram(int start, int codePointCount) {
            this.start = start;
            this.codePointCount = codePointCount;
        }

        public int getStart() {
            return start;
        }

        public int getCodePointCount() {
            return codePointCount;
        }

        /** Returns this gram as a string from the input string */
        public String extractFrom(String input) {
            return extractFrom(new UnicodeString(input));
        }

        /** Returns this gram as a string from the input string */
        public String extractFrom(UnicodeString input) {
            return input.substring(start, codePointCount).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if ( ! (o instanceof Gram)) return false;

            Gram gram = (Gram)o;
            if (codePointCount != gram.codePointCount) return false;
            if (start != gram.start) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = start;
            result = 31 * result + codePointCount;
            return result;
        }

    }

    /**
     * A string wrapper with some convenience methods for dealing with UTF-16 surrogate pairs
     * (a crime against humanity for which we'll be negatively impacted for at least the next million years).
     */
    private static class UnicodeString {

        private final String s;

        public UnicodeString(String s) {
            this.s = s;
        }

        /** Substring in code point space */
        public UnicodeString substring(int start, int codePoints) {
            int cps = codePoints * 2 <= s.length() - start ? codePoints
                                                           : Math.min(codePoints, s.codePointCount(start, s.length()));
            return new UnicodeString(s.substring(start, s.offsetByCodePoints(start, cps)));
        }

        /** Returns the position count code points after start (which may be past the end of the string) */
        public int skip(int codePointCount, int start) {
            int index = start;
            for (int i = 0; i < codePointCount; i++) {
                index = next(index);
                if (index > s.length()) break;
            }
            return index;
        }

        /** Returns the index of the next code point after start (which may be past the end of the string) */
        public int next(int index) {
            int next = index + 1;
            if (next < s.length() && Character.isLowSurrogate(s.charAt(next)))
                next++;
            return next;
        }

        /** Returns the number of positions (not code points) in this */
        public int length() { return s.length(); }

        /** Returns the number of code points in this */
        public int codePointCount() { return s.codePointCount(0, s.length()); }

        public int codePointAt(int index) {
            return s.codePointAt(index);
        }

        @Override
        public String toString() { return s; }

    }

}
