// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.simple;

import com.yahoo.document.select.rule.IdNode;

/**
 * @author baldersheim
 */
public class IdSpecParser extends Parser {
    private IdNode id;
    public IdNode getId() { return id; }
    boolean isUserSpec() { return "user".equals(id.getField()); }
    public boolean parse(CharSequence s) {
        boolean retval = false;
        int pos = eatWhite(s);
        if (pos+1 < s.length()) {
            if (icmp(s.charAt(pos), 'i') && icmp(s.charAt(pos+1),'d')) {
                pos += 2;
                if (pos < s.length()) {
                    switch (s.charAt(pos)) {
                    case '.':
                        {
                            int startPos = ++pos;
                            for (;pos < s.length() && (Character.toLowerCase(s.charAt(pos)) >= 'a') && (Character.toLowerCase(s.charAt(pos)) <= 'z'); pos++);
                            int len = pos - startPos;
                            if (((len == 4) && "user".equalsIgnoreCase(s.subSequence(startPos, startPos + 4).toString())) ||
                                ((len == 5) && "group".equalsIgnoreCase(s.subSequence(startPos, startPos + 5).toString())) ||
                                ((len == 6) && "scheme".equalsIgnoreCase(s.subSequence(startPos, startPos + 6).toString())) ||
                                ((len == 8) && "specific".equalsIgnoreCase(s.subSequence(startPos, startPos + 8).toString())) ||
                                ((len == 9) && "namespace".equalsIgnoreCase(s.subSequence(startPos, startPos + 9).toString())))
                            {
                                retval = true;
                                id = new IdNode().setField(s.subSequence(startPos, startPos + len).toString());
                            } else {
                                pos = startPos;
                            }
                        }
                        break;
                    case '!':
                    case '<':
                    case '>':
                    case '=':
                    case '\t':
                    case '\n':
                    case '\r':
                    case ' ':
                        {
                            retval = true;
                            id = new IdNode();
                        }
                        break;
                    default:
                        break;
                    }
                }
            }
        }
        setRemaining(s.subSequence(pos, s.length()));

        return retval;
    }

}
