// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * The id of a document type.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentTypeId {
    private int id;

    public DocumentTypeId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean equals(Object o) {
        if (!(o instanceof DocumentTypeId)) return false;
        DocumentTypeId other = (DocumentTypeId) o;
        return other.id == this.id;
    }

    public int hashCode() {
        return id;
    }

    public String toString() {
        return "" + id;
    }
}
