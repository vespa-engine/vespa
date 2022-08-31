// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

/**
 * An immutable class representing a default value of a config variable
 *
 * @author bratseth
 */
public class DefaultValue {

    private String value = null;

     // The variable type. Always set UNLESS the value is null.
    private DefLine.Type type = null;

    /** Null value. */
    public DefaultValue() {
    }

    /** A default value with the given value and type. */
    public DefaultValue(String value, DefLine.Type type) {
        this.value = value;
        this.type = type;
    }

    /** Returns the toString of the default value. */
    public String getValue() {
        return value;
    }

    /** Returns the string representation of this value. */
    public String getStringRepresentation() {
        if (value == null)
            return "null";
        else if ("bool".equals(type.getName()))
            return value;
        else if ("int".equals(type.getName()))
            return value;
        else if ("long".equals(type.getName()))
            return value;
        else if ("double".equals(type.getName()))
            return value;
        else if ("enum".equals(type.getName()))
            return value;
        else {
            // building a string, do unicode-escaping
            StringBuilder sb = new StringBuilder();
            for (char c : value.toCharArray()) {
                if (c > '\u007f') {
                    sb.append(String.format("\\u%04X", (int) c));
                } else {
                    sb.append(c);
                }
            }
            return "\"" + sb + "\"";
        }
    }

}
