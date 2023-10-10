// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * An immutable list of special tokens - strings which should override the normal tokenizer semantics
 * and be tokenized into a single token. Special tokens are case insensitive.
 *
 * @author bratseth
 */
public class SpecialTokens {

    private static final SpecialTokens empty = new SpecialTokens("(empty)", List.of());

    private final String name;
    private final int maximumLength;
    private final List<Token> tokens;
    private final Map<String, String> tokenMap;

    public SpecialTokens(String name,  List<Token> tokens) {
        tokens.stream().peek(token -> token.validate());
        List<Token> mutableTokens = new ArrayList<>(tokens);
        Collections.sort(mutableTokens);
        this.name = name;
        this.maximumLength = tokens.stream().mapToInt(token -> token.token().length()).max().orElse(0);
        this.tokens = List.copyOf(mutableTokens);
        this.tokenMap = tokens.stream().collect(Collectors.toUnmodifiableMap(t -> t.token(), t -> t.replacement()));
    }

    /** Returns the name of this special tokens list */
    public String name() {
        return name;
    }

    /**
     * Returns the tokens of this as an immutable map from token to replacement.
     * Tokens which do not have a replacement token maps to themselves.
     */
    public Map<String, String> asMap() { return tokenMap; }

    /**
     * Returns the special token starting at the start of the given string, or null if no
     * special token starts at this string
     *
     * @param string the string to search for a special token at the start position
     * @param substring true to allow the special token to be followed by a character which does not
     *        mark the end of a token
     */
    public Token tokenize(String string, boolean substring) {
        // XXX detonator pattern token.length may be != the length of the
        // matching data in string, ref caseIndependentLength(String)
        String input = toLowerCase(string.substring(0, Math.min(string.length(), maximumLength)));
        for (Iterator<Token> i = tokens.iterator(); i.hasNext();) {
            Token special = i.next();

            if (input.startsWith(special.token())) {
                if (string.length() == special.token().length() || substring || tokenEndsAt(special.token().length(), string))
                    return special;
            }
        }
        return null;
    }

    private boolean tokenEndsAt(int position, String string) {
        return !Character.isLetterOrDigit(string.charAt(position));
    }

    public static SpecialTokens empty() { return empty; }

    /** An immutable special token */
    public final static class Token implements Comparable<Token> {

        private final String token;
        private final String replacement;

        /** Creates a special token */
        public Token(String token) {
            this(token, null);
        }

        /** Creates a special token which will be represented by the given replacement token */
        public Token(String token, String replacement) {
            this.token = toLowerCase(token);
            if (replacement == null || replacement.trim().equals(""))
                this.replacement = this.token;
            else
                this.replacement = toLowerCase(replacement);
        }

        /** Returns the special token */
        public String token() { return token; }

        /** Returns the token to replace occurrences of this by, which equals token() unless this has a replacement. */
        public String replacement() { return replacement; }

        @Override
        public int compareTo(Token other) {
            if (this.token().length() < other.token().length()) return 1;
            if (this.token().length() == other.token().length()) return 0;
            return -1;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if ( ! (other instanceof Token)) return false;
            return Objects.equals(this.token, ((Token)other).token);
        }

        @Override
        public int hashCode() { return token.hashCode(); }

        @Override
        public String toString() {
            return "token '" + token + "'" + (replacement.equals(token) ? "" : " replacement '" + replacement + "'");
        }

        private void validate() {
            // XXX not fool proof length test, should test codepoint by codepoint for mixed case user input? not even that will necessarily be 100% robust...
            String asLow = toLowerCase(token);
            // TODO: Put along with the global toLowerCase
            String asHigh = token.toUpperCase(Locale.ENGLISH);
            if (asLow.length() != token.length() || asHigh.length() != token.length()) {
                throw new IllegalArgumentException("Special token '" + token + "' has case sensitive length. " +
                                                   "Please report this to the Vespa team.");
            }
        }

    }

}
