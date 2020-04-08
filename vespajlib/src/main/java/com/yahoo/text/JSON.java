// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Static methods for working with JSON.
 *
 * @author bratseth
 */
public final class JSON {

    private static final ObjectMapper mapper = new ObjectMapper();

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

    /**
     * Test whether two JSON strings are equal, e.g. the order of fields in an object is irrelevant.
     *
     * <p>When comparing two numbers of the two JSON strings, the result is only guaranteed to be
     * correct if (a) both are integers (without fraction and exponent) and each fits in a long, or
     * (b) both are non-integers, were syntactically identical, and fits in a double.</p>
     */
    public static boolean equals(String left, String right) {
        JsonNode leftJsonNode = uncheck(() -> mapper.readTree(left));
        JsonNode rightJsonNode = uncheck(() -> mapper.readTree(right));
        return Objects.equals(leftJsonNode, rightJsonNode);
    }

}
