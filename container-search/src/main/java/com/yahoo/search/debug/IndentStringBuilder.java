// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import java.io.Serializable;

/**
 * A StringBuilder that also handles indentation for append operations.
 * @author tonytv
 */
@SuppressWarnings("serial")
final class IndentStringBuilder implements Serializable, Appendable, CharSequence {
    private final StringBuilder builder = new StringBuilder();
    private final String singleIndentation;

    private int level = 0;
    private boolean newline = true;

    private void appendIndentation() {
        if (newline) {
            for (int i=0; i<level; i++) {
                builder.append(singleIndentation);
            }
        }
        newline  = false;
    }

    public IndentStringBuilder(String singleIndentation) {
        this.singleIndentation = singleIndentation;
    }

    public IndentStringBuilder() {
        this("  ");
    }

    public void resetIndentLevel(int level) {
        this.level = level;
    }

    //returns the indent level before indenting.
    public int newlineAndIndent() {
        newline();
        return indent();
    }

    //returns the indent level before indenting.
    public int indent() {
        return level++;
    }

    public IndentStringBuilder newline() {
        newline = true;
        builder.append('\n');
        return this;
    }

    public IndentStringBuilder append(Object o) {
        appendIndentation();
        builder.append(o);
        return this;
    }

    public IndentStringBuilder append(String s) {
        appendIndentation();
        builder.append(s);
        return this;
    }

    public IndentStringBuilder append(CharSequence charSequence) {
        appendIndentation();
        builder.append(charSequence);
        return this;
    }

    public IndentStringBuilder append(CharSequence charSequence, int i, int i1) {
        appendIndentation();
        builder.append(charSequence, i, i1);
        return this;
    }

    public IndentStringBuilder append(char c) {
        appendIndentation();
        builder.append(c);
        return this;
    }

    public String toString() {
        return builder.toString();
    }

    public int length() {
        return builder.length();
    }

    public char charAt(int i) {
        return builder.charAt(i);
    }

    public CharSequence subSequence(int i, int i1) {
        return builder.subSequence(i, i1);
    }

}
