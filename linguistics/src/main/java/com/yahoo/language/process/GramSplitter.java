// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        if (input == null) {
            throw new NullPointerException("input cannot be null");
        }
        if (n < 1) {
            throw new IllegalArgumentException("n (gram size) cannot be smaller than 1, was " + n);
        }
        return new GramSplitterIterator(input, n, characterClasses);
    }

    public static class GramSplitterIterator implements Iterator<Gram> {

        private final CharacterClasses characterClasses;

        /**
         * Text to split
         */
        private final String input;

        /**
         * Gram size
         */
        private final int n;

        /**
         * Current index
         */
        private int i = 0;

        /**
         * Whether the last thing that happened was being on a separator (including the start of the string)
         */
        private boolean isFirstAfterSeparator = true;

        /**
         * The next gram or null if not determined yet
         */
        private Gram nextGram = null;

        public GramSplitterIterator(String input, int n, CharacterClasses characterClasses) {
            this.input = input;
            this.n = n;
            this.characterClasses = characterClasses;
        }

        @Override
        public boolean hasNext() {
            if (nextGram != null) {
                return true;
            }
            nextGram = findNext();
            return nextGram != null;
        }

        @Override
        public Gram next() {
            Gram currentGram = nextGram;
            if (currentGram == null) {
                currentGram = findNext();
            }
            if (currentGram == null) {
                throw new NoSuchElementException("No next gram at position " + i);
            }
            nextGram = null;
            return currentGram;
        }

        private Gram findNext() {
            // Skip to next word character
            while (i < input.length() && !characterClasses.isLetterOrDigit(input.codePointAt(i))) {
                i++;
                isFirstAfterSeparator = true;
            }
            if (i >= input.length()) {
                return null;
            }

            String gram = input.substring(i, Math.min(i + n, input.length()));
            int nonWordChar = indexOfNonWordChar(gram);
            if (nonWordChar == 0) {
                throw new RuntimeException("Programming error");
            }
            if (nonWordChar > 0) {
                gram = gram.substring(0, nonWordChar);
            }

            if (gram.length() == n) { // normal case: got a full length gram
                i++;
                isFirstAfterSeparator = false;
                return new Gram(i - 1, gram.length());
            } else { // gram is too short due either to a non-word separator or end of string
                if (isFirstAfterSeparator) { // make a gram anyway
                    i++;
                    isFirstAfterSeparator = false;
                    return new Gram(i - 1, gram.length());
                } else { // skip to next
                    i += gram.length() + 1;
                    isFirstAfterSeparator = true;
                    return findNext();
                }
            }
        }

        private int indexOfNonWordChar(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (!characterClasses.isLetterOrDigit(s.codePointAt(i))) {
                    return i;
                }
            }
            return -1;
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
            while (hasNext()) {
                gramList.add(next().extractFrom(input));
            }
            return Collections.unmodifiableList(gramList);
        }
    }

    /**
     * An immutable start index and length pair
     */
    public static final class Gram {

        private int start, length;

        public Gram(int start, int length) {
            this.start = start;
            this.length = length;
        }

        public int getStart() {
            return start;
        }

        public int getLength() {
            return length;
        }

        /**
         * Returns this gram as a string from the input string
         */
        public String extractFrom(String input) {
            return input.substring(start, start + length);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Gram)) {
                return false;
            }

            Gram gram = (Gram)o;

            if (length != gram.length) {
                return false;
            }
            if (start != gram.start) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = start;
            result = 31 * result + length;
            return result;
        }
    }
}
