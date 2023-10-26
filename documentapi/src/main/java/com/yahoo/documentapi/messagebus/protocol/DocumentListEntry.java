// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.Document;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.vespa.objects.Serializer;

public class DocumentListEntry {

    private Document doc;
    private long timestamp;
    private boolean removeEntry;

    public DocumentListEntry(Document doc, long timestamp, boolean removeEntry) {
        this.doc = doc;
        this.timestamp = timestamp;
        this.removeEntry = removeEntry;
    }

    public void serialize(Serializer buf) {
        buf.putLong(null, timestamp);
        doc.serialize(buf);
        buf.putByte(null, (byte)(removeEntry ? 1 : 0));
    }

    public static int getApproxSize() {
        return 60; // optimzation. approximation is sufficient
    }

    public DocumentListEntry(DocumentDeserializer buf) {
        timestamp = buf.getLong(null);
        doc = new Document(buf);
        removeEntry = buf.getByte(null) > 0;
    }

    public Document getDocument() {
        return doc;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRemoveEntry() {
        return removeEntry;
    }
}
