// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.Field;

/**
 * Represents a document reference. Contains the document reference field and the search instance of the referred document.
 *
 * @author bjorncs
 */
public class DocumentReference {

    private final Field referenceField;
    private final Schema targetSchema;

    public DocumentReference(Field referenceField, Schema targetSchema) {
        this.referenceField = referenceField;
        this.targetSchema = targetSchema;
    }

    public Field referenceField() {
        return referenceField;
    }

    public Schema targetSearch() {
        return targetSchema;
    }
}
