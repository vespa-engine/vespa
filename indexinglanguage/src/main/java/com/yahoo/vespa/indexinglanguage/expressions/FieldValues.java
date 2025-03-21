// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
public interface FieldValues extends FieldTypes {

    FieldValue getInputValue(String fieldName);
    FieldValue getInputValue(FieldPath fieldPath);

    FieldValues setOutputValue(String fieldName, FieldValue fieldValue, Expression expression);

    /** Returns true if this has values for all possibly existing inputs, false if it represents a partial set of values. */
    boolean isComplete();

}
