// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.simple;

/**
 * @author baldersheim
 */
public abstract class Parser {
    public abstract boolean parse(CharSequence s);
    public CharSequence getRemaining() { return remaining; }
    protected void setRemaining(CharSequence r) { remaining = r; }
    private CharSequence remaining;
    protected int eatWhite(CharSequence s) {
        int pos = 0;
        for (;pos < s.length() && Character.isSpaceChar(s.charAt(pos)); pos++);
        return pos;
    }
    protected boolean icmp(char c, char l) {
        return Character.toLowerCase(c) == l;
    }
}
