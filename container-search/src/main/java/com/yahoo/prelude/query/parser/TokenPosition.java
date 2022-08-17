// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import java.util.List;

/**
 * An iterator-like view of a list of tokens, but typed, random-accessible
 * and with more convenience methods
 *
 * @author bratseth
 */
final class TokenPosition {

    private List<Token> tokenList;

    private int position = 0;

    /**
     * Creates an empty token position which must be {@link #initialize initialized}
     * before use
     */
    public TokenPosition() {}

    /**
     * Initializes this token position. Must be done once or more before use
     *
     * @param tokens a list of tokens, which is not modified, and not used
     *        outside the calling thread
     */
    public void initialize(List<Token> tokens) {
        this.tokenList = tokens;
        position = 0;
    }

    /**
     * Returns the current token without changing the position.
     * Returns null (no exception) if there are no more tokens.
     */
    public Token current() {
        return current(0);
    }

    /**
     * Returns the current token without changing the position,
     * and without ignoring spaces.
     * Returns null (no exception) if there are no more tokens.
     */
    public Token currentNoIgnore() {
        return currentNoIgnore(0);
    }

    /**
     * Returns the token at <code>offset</code> steps from here.
     * Null (no exception) if there is no token at that position
     */
    public Token current(int offset) {
        int i = position + offset;

        while (i < tokenList.size()) {
            Token token = tokenList.get(i++);

            if (token.kind != Token.Kind.SPACE) {
                return token;
            }
        }
        return null;
    }

    /**
     * Returns the token at <code>offset</code> steps from here,
     * without ignoring spaces.
     * Null (no exception) if there is no token at that position
     */
    public Token currentNoIgnore(int offset) {
        if (tokenList.size() <= position + offset) {
            return null;
        }
        return tokenList.get(position + offset);
    }

    /**
     * Returns whether the current token is of the given kind.
     * False also if there is no token at the current position
     */
    public boolean currentIs(Token.Kind kind) {
        Token current = current();

        if (current == null) {
            return false;
        }
        return current.kind == kind;
    }

    /**
     * Returns whether the current token is of the given kind,
     * without skipping spaces.
     * False also if there is no token at the current position
     */
    public boolean currentIsNoIgnore(Token.Kind kind) {
        Token current = currentNoIgnore();

        if (current == null) {
            return false;
        }
        return current.kind == kind;
    }

    /** Returns whether more tokens are available */
    public boolean hasNext() {
        return tokenList.size() > (position + 1);
    }

    /**
     * Returns the current token and increases the position by one.
     * Returns null (no exception) if there are no more tokens
     */
    public Token next() {
        // Go to the next-non-space. Then set token, then increase position by one
        while (position < tokenList.size()) {
            Token current = tokenList.get(position++);

            if (current.kind != Token.Kind.SPACE) {
                return current;
            }
        }
        return null;
    }

    /** Skips past the current token */
    public void skip() {
        next();
    }

    /** Skips to the next token, even if the next is a space */
    public void skipNoIgnore() {
        position++;
    }

    /** Sets the position */
    public void setPosition(int position) {
        this.position = position;
    }

    /** Returns the current position */
    public int getPosition() {
        return position;
    }

    /**
     * Skips one or more tokens of the given kind
     *
     * @return true if at least one was skipped, false if there was none
     */
    public boolean skipMultiple(Token.Kind kind) {
        boolean skipped = false;

        while (hasNext() && current().kind == kind) {
            skipped = true;
            skip();
        }
        return skipped;
    }

    /**
     * Skips one or more tokens of the given kind, without ignoring spaces
     *
     * @return true if at least one was skipped, false if there was none
     */
    public boolean skipMultipleNoIgnore(Token.Kind kind) {
        boolean skipped = false;

        while (hasNext() && currentNoIgnore().kind == kind) {
            skipped = true;
            skip();
        }
        return skipped;
    }

    /**
     * Skips one or zero items of the given kind.
     *
     * @return true if one item was skipped, false if none was, or if there are no more tokens
     */
    public boolean skip(Token.Kind kind) {
        Token current = current();

        if (current == null || current.kind != kind) {
            return false;
        }

        skip();
        return true;
    }

    /**
     * Skips one or zero items of the given kind, without ignoring spaces
     *
     * @return true if one item was skipped, false if none was or if there are no more tokens
     */
    public boolean skipNoIgnore(Token.Kind kind) {
        Token current = currentNoIgnore();

        if (current == null || current.kind != kind) return false;

        skipNoIgnore();
        return true;
    }
    
    @Override
    public String toString() {
        return "token " + current();
    }

}
