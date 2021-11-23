// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.yql;

import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.CharStream;

/**
 * This class supports case-insensitive lexing by wrapping an existing
 * {@link CharStream} and forcing the lexer to see only lowercase characters.
 * Grammar literals should then be only lower case such as 'begin'. The text of the character
 * stream is unaffected. Example: input 'BeGiN' would match lexer rule
 * 'begin', but getText() would return 'BeGiN'.
 * It is based on https://github.com/antlr/antlr4/blob/master/doc/resources/CaseChangingCharStream.java
 */
class CaseInsensitiveCharStream implements CharStream {

    final CharStream stream;

    /**
     * Constructs a new CaseChangingCharStream wrapping the given {@link CharStream} forcing
     * all characters lower case.
     * @param stream The stream to wrap.
     */
    CaseInsensitiveCharStream(CharStream stream) {
        this.stream = stream;
    }

    @Override
    public String getText(Interval interval) {
        return stream.getText(interval);
    }

    @Override
    public void consume() {
        stream.consume();
    }

    @Override
    public int LA(int i) {
        int c = stream.LA(i);
        if (c <= 0) {
            return c;
        }
        return Character.toLowerCase(c);
    }

    @Override
    public int mark() {
        return stream.mark();
    }

    @Override
    public void release(int marker) {
        stream.release(marker);
    }

    @Override
    public int index() {
        return stream.index();
    }

    @Override
    public void seek(int index) {
        stream.seek(index);
    }

    @Override
    public int size() {
        return stream.size();
    }

    @Override
    public String getSourceName() {
        return stream.getSourceName();
    }
}
