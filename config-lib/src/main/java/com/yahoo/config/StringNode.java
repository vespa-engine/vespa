// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import com.yahoo.config.text.StringUtilities;

/**
 * A StringNode class represents a string in a {@link ConfigInstance}.
 *
 * @author larschr
 */
public class StringNode extends LeafNode<String> {

    /**
     * Creates a new un-initialized StringNode.
     */
    public StringNode() {
    }

    /**
     * Creates a new StringNode, initialized to <code>value</code>.
     *
     * @param value the value of this StringNode.
     */
    public StringNode(String value) {
        super(true);
        this.value = value;
    }

    /**
     * Returns the value of this string. Same as {@link #getValue()}
     * since the value of this node is a String (but implementations
     * in other {@link LeafNode} subclasses differ).
     *
     * @return the string representation of this StringNode, or null if
     *         the value is explicitly set to null
     */
    public String value() {
        return value;
    }

    @Override
    public String getValue() {
        return value();
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : '"' + StringUtilities.escape(getValue()) + '"';
    }

    /**
     * Remove character escape codes.
     *
     * @param string escaped string
     * @return unescaped string
     */
    public static String unescapeQuotedString(String string) {
        StringBuilder sb = new StringBuilder(string);
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\\') {
                sb.deleteCharAt(i);
                if (i == sb.length()) {
                    throw new IllegalArgumentException("Parse error" + string);
                }
                switch (sb.charAt(i)) {
                    case 'n' -> sb.setCharAt(i, '\n');
                    case 'r' -> sb.setCharAt(i, '\r');
                    case 't' -> sb.setCharAt(i, '\t');
                    case 'f' -> sb.setCharAt(i, '\f');
                    case 'x' -> {
                        if (i + 2 >= sb.length()) {
                            throw new IllegalArgumentException("Could not parse hex value " + string);
                        }
                        sb.setCharAt(i, (char) Integer.parseInt(sb.substring(i + 1, i + 3), 16));
                        sb.delete(i + 1, i + 3);
                    }
                    case '\\' -> sb.setCharAt(i, '\\');
                }
            }
        }

        if (sb.length() > 0 && (sb.charAt(0) == '"') && sb.charAt(sb.length() - 1) == '"') {
            sb.deleteCharAt(sb.length() - 1);//remove last quote
            if (sb.length() > 0) {
                sb.deleteCharAt(0);  //remove first quote
            }
        }
        return sb.toString();
    }

    /**
     * Sets the value of this string from the string representation
     * of this value in the (escaped) input configuration. The value
     * supplied to this method needs un-escaping and will be
     * un-escaped.
     *
     * @param value the new value of this node.
     */
    @Override
    protected boolean doSetValue(String value) {
        if (value.startsWith("\"") && value.endsWith("\""))
            this.value = unescapeQuotedString(value);
        else {
            //TODO: unquoted strings can be probably be prohibited now.(?) -gv
            this.value = value;
        }
        return true;
    }

}
