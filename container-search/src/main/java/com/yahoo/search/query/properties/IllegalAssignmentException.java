// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.tensor.Tensor;

/**
 * Thrown won an illegal property assignment.
 */
public class IllegalAssignmentException extends IllegalArgumentException {

    public IllegalAssignmentException(CompoundName name, Object value, Throwable cause) {
        super("Could not set '" + name + "' to '" + toShortString(value) + "'", cause);
    }

    public IllegalAssignmentException(CompoundName name, Object value, String message) {
        super("Could not set '" + name + "' to '" + toShortString(value) + "': " + message);
    }

    private static String toShortString(Object value) {
        if (value == null) return "(null)";
        if ( ! (value instanceof Tensor)) return value.toString();
        return ((Tensor)value).toAbbreviatedString();
    }

}
