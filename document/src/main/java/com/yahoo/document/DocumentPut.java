// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * @author Vegard Sjonfjell
 */
public class DocumentPut extends DocumentOperation {

    private final Document document;

    public DocumentPut(Document document) {
        this.document = document;
    }

    public DocumentPut(DocumentType docType, DocumentId docId) {
        this.document = new Document(docType, docId);
    }

    public DocumentPut(DocumentType docType, String docId) {
        this.document = new Document(docType, docId);
    }

    public Document getDocument() {
        return document;
    }

    public DocumentId getId() {
        return document.getId();
    }

    /**
     * Copy constructor
     * @param other DocumentPut to copy
     */
    public DocumentPut(DocumentPut other) {
        super(other);
        this.document = new Document(other.getDocument());
    }

    /**
     * Base this DocumentPut on another, but use newDocument as the Document.
     */
    public DocumentPut(DocumentPut other, Document newDocument) {
        super(other);
        this.document = newDocument;
    }

    @Override
    public String toString() {
        return "put of document " + getId();
    }

}
