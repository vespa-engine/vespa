// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;

import java.util.ArrayList;

public class FieldCollection extends ArrayList<Field> implements FieldSet {
    DocumentType docType;

    public FieldCollection(DocumentType type) {
        docType = type;
    }

    public DocumentType getDocumentType() {
        return docType;
    }

    @Override
    public boolean contains(FieldSet o) {
        if (o instanceof DocIdOnly || o instanceof NoFields) {
            return true;
        }

        if (o instanceof Field) {
            return super.contains(o);
        } else if (o instanceof FieldCollection) {
            FieldCollection c = (FieldCollection)o;

            for (Field f : c) {
                if (!super.contains(f)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public FieldSet clone() {
        FieldCollection c = new FieldCollection(docType);
        c.addAll(this);
        return c;
    }
}
