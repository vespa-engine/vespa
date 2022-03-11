// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public class FieldOperationApplierForStructs extends FieldOperationApplier {

    @Override
    public void process(SDDocumentType sdoc) {
        for (SDDocumentType type : sdoc.getAllTypes()) {
            if (type.isStruct()) {
                apply(type);
            }
        }
    }

}
