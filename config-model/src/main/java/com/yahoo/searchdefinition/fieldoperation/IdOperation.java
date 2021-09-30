// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public class IdOperation implements FieldOperation {

    private SDDocumentType document;
    private int fieldId;

    public SDDocumentType getDocument() {
        return document;
    }

    public void setDocument(SDDocumentType document) {
        this.document = document;
    }

    public int getFieldId() {
        return fieldId;
    }

    public void setFieldId(int fieldId) {
        this.fieldId = fieldId;
    }

    public void apply(SDField field) {
         document.setFieldId(field, fieldId);
    }

}
