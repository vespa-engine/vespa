// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.simple;

import com.yahoo.document.select.rule.LiteralNode;

/**
 * @author baldersheim
 */
public class StringParser extends Parser {
    private LiteralNode value;
    public LiteralNode getValue() { return value; }
    public boolean parse(CharSequence s) {
        boolean retval = false;
        int pos = eatWhite(s);
        if (pos + 1 < s.length()) {
        if (s.charAt(pos++) == '"') {
            StringBuilder str = new StringBuilder();
            for(; (pos < s.length()) && (s.charAt(pos) != '"');pos++) {
                if ((pos < s.length()) && (s.charAt(pos) == '\\')) {
                    pos++;
                }
                str.append(s.charAt(pos));
            }
            if (s.charAt(pos) == '"') {
                pos++;
                retval = true;
                value = new LiteralNode(str.toString());
            }
        }

        setRemaining(s.subSequence(pos, s.length()));
    }
        return retval;
    }
}
