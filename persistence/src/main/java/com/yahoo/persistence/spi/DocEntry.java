// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;

/**
 * Class that represents an entry retrieved by iterating.
 */
public class DocEntry implements Comparable<DocEntry> {


    @Override
    public int compareTo(DocEntry docEntry) {
        return new Long(timestamp).compareTo(docEntry.getTimestamp());
    }

    public enum Type {
        PUT_ENTRY,
        REMOVE_ENTRY
    }

    long timestamp;
    Type type;

    DocumentId docId;
    Document document;

    public DocEntry(long timestamp, Document doc, Type type, DocumentId docId) {
        this.timestamp = timestamp;
        this.type = type;
        this.docId = docId;
        document = doc;
    }


    public DocEntry(long timestamp, Document doc) {
        this(timestamp, doc, Type.PUT_ENTRY, doc.getId());
    }

    public DocEntry(long timestamp, DocumentId docId) {
        this(timestamp, null, Type.REMOVE_ENTRY, docId);
    }

    public DocEntry(long timestamp, Type type) {
        this(timestamp, null, type, null);
    }

    public Type getType() { return type; }

    public long getTimestamp() { return timestamp; }

    public DocumentId getDocumentId() { return docId; }

    public Document getDocument() { return document; }
}
