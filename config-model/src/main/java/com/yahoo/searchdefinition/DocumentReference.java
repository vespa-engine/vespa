// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.Field;

/**
 * Represents a document reference. Contains the document reference field and the search instance of the referred document.
 *
 * @author bjorncs
 */
public class DocumentReference {

    private final Field referenceField;
    private final Search targetSearch;

    public DocumentReference(Field referenceField, Search targetSearch) {
        this.referenceField = referenceField;
        this.targetSearch = targetSearch;
    }

    public Field referenceField() {
        return referenceField;
    }

    public Search targetSearch() {
        return targetSearch;
    }
}
