// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * Transient class. Only for internal use in document and documentapi.
 *
 * @author baldersheim
 * @author toregge
 */
public class DocumentGet extends DocumentOperation {

    private final DocumentId docId;

    public DocumentGet(DocumentId docId) { this.docId = docId; }

    @Override
    public DocumentId getId() { return docId; }

    @Override
    public void setCondition(TestAndSetCondition condition) {
        throw new UnsupportedOperationException("conditional DocumentGet is not supported");
    }

    @Override
    public String toString() {
        return "DocumentGet '" + docId + "'";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentGet)) return false;
        DocumentGet that = (DocumentGet) o;
        if (!docId.equals(that.docId)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return docId.hashCode();
    }
}
