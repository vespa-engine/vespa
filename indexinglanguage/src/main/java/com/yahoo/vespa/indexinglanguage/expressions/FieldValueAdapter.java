// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
public interface FieldValueAdapter extends FieldTypeAdapter {

    public FieldValue getInputValue(String fieldName);
    public FieldValue getInputValue(FieldPath fieldPath);

    public FieldValueAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue);
}
