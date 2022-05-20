// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDDocumentType;

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
