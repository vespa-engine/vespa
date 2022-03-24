// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.select.rule.AttributeNode;

/**
 * @author Simon Thoresen Hult
 */
public enum Result {

    /**
     * Defines all enumeration constants.
     */
    TRUE,
    FALSE,
    INVALID;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
    
    /**
     * Inverts the result value to the appropriate value. True → False, False → True and Invalid → Invalid.
     * @return inverted result
     */
    public static Result invert(Result result) {
        if (result == Result.TRUE) return Result.FALSE;
        if (result == Result.FALSE) return Result.TRUE;
        return Result.INVALID;
    }

    /**
     * Converts the given object value into an instance of this Result enumeration.
     *
     * @param value The object to convert.
     * @return The corresponding result value.
     */
    public static Result toResult(Object value) {
        if (value == null || value == Result.FALSE || value == Boolean.FALSE ||
            (value instanceof Number && ((Number)value).doubleValue() == 0)) {
            return Result.FALSE;
        } else if (value == INVALID) {
            return Result.INVALID;
        } else if (value instanceof AttributeNode.VariableValueList) {
            return ((AttributeNode.VariableValueList)value).isEmpty() ? Result.FALSE : Result.TRUE;
        } else if (value instanceof ResultList) {
            return ((ResultList)value).toResult();
        } else {
            return Result.TRUE;
        }
    }
}
