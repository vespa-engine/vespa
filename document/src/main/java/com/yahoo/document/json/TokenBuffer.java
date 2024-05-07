// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * Helper class to enable lookahead in the token stream.
 *
 * @author Steinar Knutsen
 */
public class TokenBuffer {

    final Deque<Token> tokens = new ArrayDeque<>();

    private int nesting = 0;

    public TokenBuffer() { }

    /** Returns whether any tokens are available in this */
    public boolean isEmpty() { return tokens.isEmpty(); }

    /** Returns the next token, or null, and updates the nesting count of this. */
    public JsonToken next() {
        advance();
        JsonToken token = current();
        updateNesting(token);
        return token;
    }

    void advance() {
        tokens.poll();
    }

    /** Returns the current token without changing position, or null if none */
    public JsonToken current() {
        return isEmpty() ? null : tokens.peek().token;
    }

    /** Returns the current token name without changing position, or null if none */
    public String currentName() {
        return isEmpty() ? null : tokens.peek().name;
    }

    /** Returns the current token text without changing position, or null if none */
    public String currentText() {
        return isEmpty() ? null : tokens.peek().text;
    }

    /**
     * Returns a sequence of remaining tokens in this, or nulls when none remain.
     * This may fill the token buffer, but not otherwise modify it.
     */
    public Supplier<Token> lookahead() {
        Iterator<Token> iterator = tokens.iterator();
        if (iterator.hasNext()) iterator.next();
        return () -> iterator.hasNext() ? iterator.next() : null;
    }

    private void add(JsonToken token, String name, String text) {
        tokens.add(new Token(token, name, text));
    }

    public void bufferObject(JsonParser parser) {
        bufferJsonStruct(parser, JsonToken.START_OBJECT);
    }

    private void bufferJsonStruct(JsonParser parser, JsonToken firstToken) {
        JsonToken token = parser.currentToken();
        Preconditions.checkArgument(token == firstToken,
                                    "Expected %s, got %s.", firstToken.name(), token);
        updateNesting(token);

        try {
            for (int nesting = addFromParser(parser); nesting > 0; nesting += addFromParser(parser))
                parser.nextValue();
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    int nestingOffset(JsonToken token) {
        if (token == null) return 0;
        if (token.isStructStart()) {
            return 1;
        } else if (token.isStructEnd()) {
            return -1;
        } else {
            return 0;
        }
    }

    int addFromParser(JsonParser tokens) throws IOException {
        add(tokens.currentToken(), tokens.currentName(), tokens.getText());
        return nestingOffset(tokens.currentToken());
    }

    void updateNesting(JsonToken token) {
        nesting += nestingOffset(token);
    }

    public int nesting() {
        return nesting;
    }

    public void skipToRelativeNesting(int relativeNesting) {
        int initialNesting = nesting();
        do next();
        while (nesting() > initialNesting + relativeNesting);
    }

    public static final class Token {

        public final JsonToken token;
        public final String name;
        public final String text;

        Token(JsonToken token, String name, String text) {
            this.token = token;
            this.name = name;
            this.text = text;
        }

        @Override
        public String toString() {
            return "Token(" + token + ", " + name + ", " + text + ")";
        }

    }

}
