// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.simple;

/**
 * @author baldersheim
 */
public class OperatorParser extends Parser {
    private String operator;
    public String getOperator() { return operator; }
    public boolean parse(CharSequence s) {
        boolean retval = false;
        int pos = eatWhite(s);

        if (pos+1 < s.length()) {
            retval = true;
            int startPos = pos;
            if (s.charAt(pos) == '=') {
                pos++;
                if ((s.charAt(pos) == '=') || (s.charAt(pos) == '~')) {
                    pos++;
                }
            } else if (s.charAt(pos) == '>') {
                pos++;
                if (s.charAt(pos) == '=') {
                    pos++;
                }
            } else if (s.charAt(pos) == '<') {
                pos++;
                if (s.charAt(pos) == '=') {
                    pos++;
                }
            } else if ((s.charAt(pos) == '!') && (s.charAt(pos) == '=')) {
                pos += 2;
            } else {
                retval = false;
            }
            if (retval) {
                operator = s.subSequence(startPos, pos).toString();
            }
        }
        setRemaining(s.subSequence(pos, s.length()));

        return retval;
    }
}
