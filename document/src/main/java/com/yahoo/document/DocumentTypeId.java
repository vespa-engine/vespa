// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * The id of a document type.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentTypeId {

    private final int id;

    public DocumentTypeId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DocumentTypeId)) return false;
        DocumentTypeId other = (DocumentTypeId) o;
        return other.id == this.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "" + id;
    }

}
