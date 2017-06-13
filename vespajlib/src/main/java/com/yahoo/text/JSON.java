// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.Map;

/**
 * Static methods for working with the map textual format which is parsed by {@link MapParser}
 *
 * @author bratseth
 */
public final class JSON {

    /** No instances */
    private JSON() {}

    /**
     * Outputs a map as a JSON 'object' string, provided that the map values
     * are either
     * <ul>
     * <li>String
     * <li>Number
     * <li>Any object whose toString returns JSON
     * </ul>
     */
    public static String encode(Map<String, ?> map) {
        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String,?> entry : map.entrySet()) {
            b.append("\"").append(escape(entry.getKey())).append("\":");
            if (entry.getValue() instanceof String)
                b.append("\"").append(escape(entry.getValue().toString())).append("\"");
            else // Number, or some other object which returns JSON
                b.append(entry.getValue());
            b.append(",");
        }
        if (b.length()>1)
            b.setLength(b.length()-1); // remove last comma
        b.append("}");
        return b.toString();
    }

    /** Returns the given string as a properly json escaped string */
    public static String escape(String s) {
        StringBuilder b = null; // lazy create to optimize for "nothing to do" case

        for (int i=0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            final int codepoint = s.codePointAt(i);
            if (codepoint == '"') {
                if (b == null)
                    b = new StringBuilder(s.substring(0, i));
                b.append('\\');
            }

            if (b != null)
                b.appendCodePoint(codepoint);
        }
        return b != null ? b.toString() : s;
    }

}
