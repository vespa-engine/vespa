// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DocumentType;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
public interface FieldValueAdapter extends FieldTypeAdapter {

    DocumentType getDocumentType();

    FieldValue getInputValue(String fieldName);
    FieldValue getInputValue(FieldPath fieldPath);

    FieldValueAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue);

}
