// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property which is markup, not content.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class MarkupFieldPart implements FieldPart {
    private String content;
    public MarkupFieldPart(String content) {
        this.content = content;
    }
    public boolean isFinal() { return true; }
    // Markup is never part of tokens as such
    public boolean isToken() { return false; }
    public void setContent(String content) {
        this.content = content;
    }
    public String getContent() { return content; }
    public String toString() { return content; }
}
