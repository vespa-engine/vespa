// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.Field;

/**
 * Represents a document reference. Contains the document reference field and the search instance of the referred document.
 *
 * @author bjorncs
 */
public class DocumentReference {

    private final Field documentReferenceField;
    private final Search search;

    public DocumentReference(Field documentReferenceField, Search search) {
        this.documentReferenceField = documentReferenceField;
        this.search = search;
    }

    public Field documentReferenceField() {
        return documentReferenceField;
    }

    public Search search() {
        return search;
    }
}
