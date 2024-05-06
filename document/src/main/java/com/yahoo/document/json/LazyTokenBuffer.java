package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A {@link TokenBuffer} which only buffers tokens when needed, i.e., when peeking.
 *
 * @author jonmv
 */
public class LazyTokenBuffer extends TokenBuffer {

    private final JsonParser parser;

    public LazyTokenBuffer(JsonParser parser) {
        this.parser = parser;
        try { addFromParser(parser); }
        catch (IOException e) { throw new IllegalArgumentException("failed parsing document JSON", e); }
        if (JsonToken.START_OBJECT != current())
            throw new IllegalArgumentException("expected start of JSON object, but got " + current());
        updateNesting(current());
    }

    void advance() {
        super.advance();
        if (tokens.isEmpty() && nesting() > 0) tokens.add(nextToken()); // Fill current token if needed and possible.
    }

    @Override
    public Supplier<Token> lookahead() {
        return new Supplier<>() {
            int localNesting = nesting();
            final Supplier<Token> buffered = LazyTokenBuffer.super.lookahead();
            @Override public Token get() {
                if (localNesting == 0)
                    return null;

                Token token = buffered.get();
                if (token == null) {
                    token = nextToken();
                    tokens.add(token);
                }
                localNesting += nestingOffset(token.token);
                return token;
            }
        };
    }

    private Token nextToken() {
        try {
            JsonToken token = parser.nextValue();
            if (token == null)
                throw new IllegalStateException("no more JSON tokens");
            return new Token(token, parser.currentName(), parser.getText());
        }
        catch (IOException e) {
            throw new IllegalArgumentException("failed reading document JSON", e);
        }
    }

}
