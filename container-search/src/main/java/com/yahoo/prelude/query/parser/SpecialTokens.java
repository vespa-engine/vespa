// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import java.util.logging.Level;
import com.yahoo.prelude.query.Substring;

import java.util.*;
import java.util.logging.Logger;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * A list of special tokens - string that should be treated as word
 * no matter what they contain. Special tokens are case insensitive.
 *
 * @author bratseth
 */
public class SpecialTokens {

    private static final Logger log = Logger.getLogger(SpecialTokens.class.getName());

    private final String name;

    private final List<SpecialToken> specialTokens = new ArrayList<>();

    private boolean frozen = false;

    private int currentMaximumLength = 0;

    /** Creates a null list of special tokens */
    public SpecialTokens() {
        this.name = "(null)";
    }

    public SpecialTokens(String name) {
        this.name = name;
    }

    /** Returns the name of this special tokens list */
    public String getName() {
        return name;
    }

    /**
     * Adds a special token to this
     *
     * @param token the special token string to add
     * @param replace the token to replace instances of the special token with, or null to keep the token
     */
    public void addSpecialToken(String token, String replace) {
        ensureNotFrozen();
        if (!caseIndependentLength(token)) {
            return;
        }
        // TODO are special tokens correctly unicode normalized in reagards to query parsing?
        final SpecialToken specialTokenToAdd = new SpecialToken(token, replace);
        currentMaximumLength = Math.max(currentMaximumLength, specialTokenToAdd.token.length());
        specialTokens.add(specialTokenToAdd);
        Collections.sort(specialTokens);
    }

    private boolean caseIndependentLength(String token) {
        // XXX not fool proof length test, should test codepoint by codepoint for mixed case user input? not even that will necessarily be 100% robust...
        String asLow = toLowerCase(token);
        // TODO put along with the global toLowerCase
        String asHigh = token.toUpperCase(Locale.ENGLISH);
        if (asLow.length() != token.length() || asHigh.length() != token.length()) {
            log.log(Level.SEVERE, "Special token '" + token + "' has case sensitive length. Ignoring the token."
                    + " Please report this message in a bug to the Vespa team.");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns the special token starting at the start of the given string, or null if no
     * special token starts at this string
     *
     * @param string the string to search for a special token at the start position
     * @param substring true to allow the special token to be followed by a character which does not
     *        mark the end of a token
     */
    public SpecialToken tokenize(String string, boolean substring) {
        // XXX detonator pattern token.length may be != the length of the
        // matching data in string, ref caseIndependentLength(String)
        final String input = toLowerCase(string.substring(0, Math.min(string.length(), currentMaximumLength)));
        for (Iterator<SpecialToken> i = specialTokens.iterator(); i.hasNext();) {
            SpecialTokens.SpecialToken special = i.next();

            if (input.startsWith(special.token())) {
                if (string.length() == special.token().length() || substring || tokenEndsAt(special.token().length(), string))
                    return special;
            }
        }
        return null;
    }

    private boolean tokenEndsAt(int position,String string) {
        return !Character.isLetterOrDigit(string.charAt(position));
    }

    /** Returns the number of special tokens in this */
    public int size() {
        return specialTokens.size();
    }

    private void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Tried to modify a frozen SpecialTokens instance.");
        }
    }

    public void freeze() {
        frozen = true;
    }

    /** An immutable special token */
    public final static class SpecialToken implements Comparable<SpecialToken> {

        private String token;

        private String replace;

        public SpecialToken(String token, String replace) {
            this.token = toLowerCase(token);
            if (replace == null || replace.trim().equals("")) {
                this.replace = this.token;
            } else {
                this.replace = toLowerCase(replace);
            }
        }

        /** Returns the special token */
        public String token() {
            return token;
        }

        /** Returns the right replace value, never null or an empty string */
        public String replace() {
            return replace;
        }

        @Override
        public int compareTo(SpecialToken other) {
            if (this.token().length() < other.token().length()) return 1;
            if (this.token().length() == other.token().length()) return 0;
            return -1;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if ( ! (other instanceof SpecialToken)) return false;
            return Objects.equals(this.token, ((SpecialToken)other).token);
        }

        @Override
        public int hashCode() { return token.hashCode(); }

        public Token toToken(int start, String rawSource) {
            return new Token(Token.Kind.WORD, replace(), true, new Substring(start, start + token.length(), rawSource)); // XXX: Unsafe?
        }

    }

}
