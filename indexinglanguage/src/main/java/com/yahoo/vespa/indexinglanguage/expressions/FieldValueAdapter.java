// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
public interface FieldValueAdapter extends FieldTypeAdapter {

    FieldValue getInputValue(String fieldName);
    FieldValue getInputValue(FieldPath fieldPath);

    FieldValueAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue);

    /** Returns true if this has values for all possibly existing inputs, or represents a partial set of values. */
    boolean isComplete();

}
