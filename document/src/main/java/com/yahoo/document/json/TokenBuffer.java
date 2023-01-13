// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;

/**
 * Helper class to enable lookahead in the token stream.
 *
 * @author Steinar Knutsen
 */
public class TokenBuffer {

    private final List<Token> tokens;

    private int position = 0;
    private int nesting = 0;

    public TokenBuffer() {
        this(new ArrayList<>());
    }

    public TokenBuffer(List<Token> tokens) {
        this.tokens = tokens;
        if (tokens.size() > 0)
            updateNesting(tokens.get(position).token);
    }

    /** Returns whether any tokens are available in this */
    public boolean isEmpty() { return remaining() == 0; }

    public JsonToken previous() {
        updateNestingGoingBackwards(current());
        position--;
        return current();
    }

    /** Returns the current token without changing position, or null if none */
    public JsonToken current() {
        if (isEmpty()) return null;
        Token token = tokens.get(position);
        if (token == null) return null;
        return token.token;
    }

    public JsonToken next() {
        position++;
        JsonToken token = current();
        updateNesting(token);
        return token;
    }

    /** Returns a given number of tokens ahead, or null if none */
    public JsonToken peek(int ahead) {
        if (tokens.size() <= position + ahead) return null;
        return tokens.get(position + ahead).token;
    }

    /** Returns the current token name without changing position, or null if none */
    public String currentName() {
        if (isEmpty()) return null;
        Token token = tokens.get(position);
        if (token == null) return null;
        return token.name;
    }

    /** Returns the current token text without changing position, or null if none */
    public String currentText() {
        if (isEmpty()) return null;
        Token token = tokens.get(position);
        if (token == null) return null;
        return token.text;
    }

    public int remaining() {
        return tokens.size() - position;
    }

    private void add(JsonToken token, String name, String text) {
        tokens.add(tokens.size(), new Token(token, name, text));
    }

    public void bufferObject(JsonToken first, JsonParser tokens) {
        bufferJsonStruct(first, tokens, JsonToken.START_OBJECT);
    }

    private void bufferJsonStruct(JsonToken first, JsonParser tokens, JsonToken firstToken) {
        int localNesting = 0;
        JsonToken t = first;

        Preconditions.checkArgument(first == firstToken,
                "Expected %s, got %s.", firstToken.name(), t);
        if (remaining() == 0) {
            updateNesting(t);
        }
        localNesting = storeAndPeekNesting(t, localNesting, tokens);
        while (localNesting > 0) {
            t = nextValue(tokens);
            localNesting = storeAndPeekNesting(t, localNesting, tokens);
        }
    }

    private int storeAndPeekNesting(JsonToken t, int nesting, JsonParser tokens) {
        addFromParser(t, tokens);
        return nesting + nestingOffset(t);
    }

    private int nestingOffset(JsonToken token) {
        if (token == null) return 0;
        if (token.isStructStart()) {
            return 1;
        } else if (token.isStructEnd()) {
            return -1;
        } else {
            return 0;
        }
    }

    private void addFromParser(JsonToken t, JsonParser tokens) {
        try {
            add(t, tokens.getCurrentName(), tokens.getText());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private JsonToken nextValue(JsonParser tokens) {
        try {
            return tokens.nextValue();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void updateNesting(JsonToken token) {
        nesting += nestingOffset(token);
    }

    private void updateNestingGoingBackwards(JsonToken token) {
        nesting -= nestingOffset(token);
    }

    public int nesting() {
        return nesting;
    }

    public Token prefetchScalar(String name) {
        int localNesting = nesting();
        int nestingBarrier = localNesting;
        Token toReturn = null;
        Iterator<Token> i;

        if (name.equals(currentName()) && current().isScalarValue()) {
            toReturn = tokens.get(position);
        } else {
            i = tokens.iterator();
            i.next(); // just ignore the first value, as we know it's not what
                      // we're looking for, and it's nesting effect is already
                      // included
            while (i.hasNext()) {
                Token t = i.next();
                if (localNesting == nestingBarrier && name.equals(t.name) && t.token.isScalarValue()) {
                    toReturn = t;
                    break;
                }
                localNesting += nestingOffset(t.token);
                if (localNesting < nestingBarrier) {
                    break;
                }
            }
        }
        return toReturn;
    }

    public void skipToRelativeNesting(int relativeNesting) {
        int initialNesting = nesting();
        do {
            next();
        } while ( nesting() > initialNesting + relativeNesting);
    }

    public List<Token> rest() {
        return tokens.subList(position, tokens.size());
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
