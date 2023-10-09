// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.simple;

import com.yahoo.document.select.rule.LiteralNode;

/**
 * @author baldersheim
 */
public class IntegerParser extends Parser {
    private LiteralNode value;
    public LiteralNode getValue() { return value; }

    public boolean parse(CharSequence s) {
        boolean retval = false;
        int pos = eatWhite(s);
        if (pos < s.length()) {
            boolean isHex = ((s.length() - pos) > 2) && (s.charAt(pos) == '0') && (s.charAt(pos+1) == 'x');
            Long v = null;
            int startPos = pos;
            if (isHex) {
                for(startPos = pos+2; (pos < s.length()) && (((s.charAt(pos) >= '0') && (s.charAt(pos) <= '9')) ||
                                                             ((s.charAt(pos) >= 'a') && (s.charAt(pos) <= 'f')) ||
                                                             ((s.charAt(pos) >= 'A') && (s.charAt(pos) <= 'F'))); pos++);
                if (pos > startPos) {
                    v = Long.valueOf(s.subSequence(startPos, pos).toString(), 16);
                    retval = true;
                }

            } else {
                if ((s.charAt(pos) == '-') || (s.charAt(pos) == '+')) {
                    pos++;
                }
                for(;(pos < s.length()) && (s.charAt(pos) >= '0') && (s.charAt(pos) <= '9') ; pos++);
                if (pos > startPos) {
                    v = Long.valueOf(s.subSequence(startPos, pos).toString(), 10);
                    retval = true;
                }
            }
            value = new LiteralNode(v);
        }
        setRemaining(s.subSequence(pos, s.length()));

        return retval;
    }
}
