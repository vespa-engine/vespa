// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property which is a possibly
 * mutable string element
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ImmutableFieldPart implements FieldPart {
    private final String content;
    private final String initContent;
    // Whether this element represents a (part of) a token or a
    // delimiter string. When splitting existing parts, the new
    // parts should inherit this state from the object they were
    // split from.
    private boolean tokenOrDelimiter;
    public ImmutableFieldPart(String initContent,
                              boolean tokenOrDelimiter) {
        this(initContent, initContent, tokenOrDelimiter);
    }
    public ImmutableFieldPart(String initContent,
                              String content,
                              boolean tokenOrDelimiter) {

        this.initContent = initContent;
        this.content = content;
        this.tokenOrDelimiter = tokenOrDelimiter;
    }
    public boolean isFinal() { return true; }
    public boolean isToken() { return tokenOrDelimiter; }
    public String getContent() { return content; }
    public String getInitContent() { return initContent; }
    public String toString() { return content; }
}
