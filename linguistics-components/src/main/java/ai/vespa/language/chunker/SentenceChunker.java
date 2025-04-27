// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.language.chunker;

import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.Chunker;
import com.yahoo.language.process.GramSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunker which splits a text into sentences.
 *
 * @author bratseth
 */
public class SentenceChunker implements Chunker {

    @Override
    public List<Chunk> chunk(String inputText, Context context) {
        var text = new UnicodeString(inputText);
        var characters = new CharacterClasses();
        List<Chunk> chunks = new ArrayList<>();
        var currentChunk = new StringBuilder();
        boolean currentHasContent = false;
        for (int i = 0; i < text.length(); ) {
            int currentChar = text.codePointAt(i);
            currentChunk.appendCodePoint(currentChar);
            if (currentHasContent && characters.isSentenceEnd(currentChar) && !characters.isSentenceEnd(text.nextCodePoint(i))) {
                chunks.add(new Chunk(currentChunk.toString()));
                currentChunk.setLength(0);
                currentHasContent = false;
            }
            else {
                currentHasContent |= ( characters.isLetterOrDigit(currentChar) || characters.isSymbol(currentChar) );
            }
            i = text.next(i);
        }
        if ( ! currentChunk.isEmpty())
            chunks.add(new Chunk(currentChunk.toString()));
        return chunks;
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

        /** Returns the index of the next code point after start, or \u0000 if the index is at the last code point */
        public int nextCodePoint(int index) {
            int next = index + 1;
            if (next < s.length() && Character.isLowSurrogate(s.charAt(next)))
                next++;
            return next >= s.length() ? '\u0000' : s.codePointAt(next);
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
