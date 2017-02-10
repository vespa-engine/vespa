// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;

/**
 * Helper class to enable lookahead in the token stream.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TokenBuffer {
    public static final class Token {
        public final JsonToken token;
        public final String name;
        public final String text;

        Token(JsonToken token, String name, String text) {
            this.token = token;
            this.name = name;
            this.text = text;
        }
    }

    private Deque<Token> buffer;
    private int nesting = 0;

    public TokenBuffer() {
        this(new ArrayDeque<>());
    }

    private TokenBuffer(Deque<Token> buffer) {
        this.buffer = buffer;
        if (buffer.size() > 0) {
            updateNesting(buffer.peekFirst().token);
        }
    }

    public JsonToken next() {
        buffer.removeFirst();
        Token t = buffer.peekFirst();
        if (t == null) {
            return null;
        }
        updateNesting(t.token);
        return t.token;
    }

    public JsonToken currentToken() {
        return buffer.peekFirst().token;
    }

    public String currentName() {
        return buffer.peekFirst().name;
    }

    public String currentText() {
        return buffer.peekFirst().text;
    }

    public int size() {
        return buffer.size();
    }

    private void add(JsonToken token, String name, String text) {
        buffer.addLast(new Token(token, name, text));
    }

    public void  bufferObject(JsonToken first, JsonParser tokens) {
        int localNesting = 0;
        JsonToken t = first;

        Preconditions.checkArgument(first == JsonToken.START_OBJECT,
                "Expected START_OBJECT, got %s.", t);
        if (size() == 0) {
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

    private int nestingOffset(JsonToken t) {
        if (t.isStructStart()) {
            return 1;
        } else if (t.isStructEnd()) {
            return -1;
        } else {
            return 0;
        }
    }

    private void addFromParser(JsonToken t, JsonParser tokens) {
        try {
            add(t, tokens.getCurrentName(), tokens.getText());
        } catch (IOException e) {
            // TODO something sane
            throw new RuntimeException(e);
        }
    }

    private JsonToken nextValue(JsonParser tokens) {
        try {
            return tokens.nextValue();
        } catch (IOException e) {
            // TODO something sane
            throw new RuntimeException(e);
        }
    }

    private void updateNesting(JsonToken t) {
        nesting += nestingOffset(t);
    }

    public int nesting() {
        return nesting;
    }

    public String dumpContents() {
        StringBuilder b = new StringBuilder();
        b.append("[nesting: ").append(nesting()).append("\n");
        for (Token t : buffer) {
            b.append("(").append(t.token).append(", \"").append(t.name).append("\", \"").append(t.text).append("\")\n");
        }
        b.append("]\n");
        return b.toString();
    }

    public void fastForwardToEndObject() {
        JsonToken t = currentToken();
        while (t != JsonToken.END_OBJECT) {
            t = next();
        }
    }

    public TokenBuffer prefetchCurrentElement() {
        Deque<Token> copy = new ArrayDeque<>();

        if (currentToken().isScalarValue()) {
            copy.add(buffer.peekFirst());
        } else {
            int localNesting = nesting();
            int nestingBarrier = localNesting;
            for (Token t : buffer) {
                copy.add(t);
                localNesting += nestingOffset(t.token);
                if (localNesting < nestingBarrier) {
                    break;
                }
            }
        }
        return new TokenBuffer(copy);
    }

    public Token prefetchScalar(String name) {
        int localNesting = nesting();
        int nestingBarrier = localNesting;
        Token toReturn = null;
        Iterator<Token> i;

        if (name.equals(currentName()) && currentToken().isScalarValue()) {
            toReturn = buffer.peekFirst();
        } else {
            i = buffer.iterator();
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
}
