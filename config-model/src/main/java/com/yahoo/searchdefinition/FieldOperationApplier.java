// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.Field;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public class FieldOperationApplier {

    public void process(SDDocumentType sdoc) {
        if (!sdoc.isStruct()) {
            apply(sdoc);
        }
    }

    protected void apply(SDDocumentType type) {
        for (Field field : type.fieldSet()) {
            apply(field);
        }
    }

    protected void apply(Field field) {
        if (field instanceof SDField) {
            SDField sdField = (SDField) field;
            sdField.applyOperations();
        }
    }

}
