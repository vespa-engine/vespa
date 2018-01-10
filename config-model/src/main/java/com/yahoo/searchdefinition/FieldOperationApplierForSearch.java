// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.Field;
import com.yahoo.searchdefinition.document.SDDocumentType;

/**
 * @author Einar M R Rosenvinge
 */
public class FieldOperationApplierForSearch extends FieldOperationApplier {
    @Override
    public void process(SDDocumentType sdoc) {
        //Do nothing
    }

    public void process(Search search) {
        for (Field field : search.extraFieldList()) {
            apply(field);
        }
    }
}
