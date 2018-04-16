// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property which is a possibly
 * mutable string element
 *
 * @author Steinar Knutsen
 */
public class StringFieldPart implements FieldPart {

    private String content;
    private final String initContent;
    // Whether this element represents a (part of) a token or a
    // delimiter string. When splitting existing parts, the new
    // parts should inherit this state from the object they were
    // split from.
    private boolean tokenOrDelimiter;

    public StringFieldPart(String content, boolean tokenOrDelimiter) {
        this.content = content;
        initContent = content;
        this.tokenOrDelimiter = tokenOrDelimiter;
    }

    @Override
    public boolean isFinal() { return false; }

    @Override
    public boolean isToken() { return tokenOrDelimiter; }

    @Override
    public String getContent() { return content; }

    public void setContent(String content) {
        this.content = content;
    }
    public String getInitContent() { return initContent; }

    @Override
    public String toString() { return content; }

}
