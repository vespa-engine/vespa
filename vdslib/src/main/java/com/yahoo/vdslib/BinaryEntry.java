// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.*;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.io.GrowableByteBuffer;

/**
 * An entry in serialized form.
 *
 * @author <a href="mailto:thomasg@yahoo-inc.com">Thomas Gundersen</a>, <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class BinaryEntry extends Entry {
    private MetaEntry metaEntry;
    private byte[] buffer;
    private DocumentTypeManager docMan;

    /**
     * Creates an entry from serialized form.
     * @param docMan The documentmanager to use when deserializing.
     * @param buffer the buffer to read the entry from
     * @param entryIndex the index of the entry in the buffer
     */
    BinaryEntry(DocumentTypeManager docMan, byte[] buffer, int entryIndex) {
        this.buffer = buffer;
        metaEntry = new MetaEntry(buffer, 4 + entryIndex * MetaEntry.SIZE);
        this.docMan = docMan;
    }

    @Override
    public boolean valid() { return buffer != null; }

    @Override
    public boolean isRemoveEntry() { return (metaEntry.flags & MetaEntry.REMOVE_ENTRY) != 0; }

    @Override
    public boolean isBodyStripped() { return (metaEntry.flags & MetaEntry.BODY_STRIPPED) != 0; }

    @Override
    public boolean isUpdateEntry() { return (metaEntry.flags & MetaEntry.UPDATE_ENTRY) != 0; }

    @Override
    public long getTimestamp() { return metaEntry.timestamp; }

    @Override
    public DocumentOperation getDocumentOperation() {
        DocumentDeserializer buf = DocumentDeserializerFactory.create42(
                docMan,
                GrowableByteBuffer.wrap(buffer, metaEntry.headerPos, metaEntry.headerLen),
                (metaEntry.bodyLen > 0) ? GrowableByteBuffer.wrap(buffer, metaEntry.bodyPos, metaEntry.bodyLen) : null
        );

        DocumentOperation op;

        if ((metaEntry.flags & MetaEntry.UPDATE_ENTRY) != 0) {
            op = new DocumentUpdate(buf);
        } else if ((metaEntry.flags & MetaEntry.REMOVE_ENTRY) != 0) {
            op = new DocumentRemove(new Document(buf).getId());
        } else {
            op = new DocumentPut(new Document(buf));
            ((DocumentPut) op).getDocument().setLastModified(getTimestamp());

        }
        return op;
    }

    @Override
    public DocumentOperation getHeader() {
        DocumentDeserializer buf = DocumentDeserializerFactory.create42(docMan, GrowableByteBuffer.wrap(buffer, metaEntry.headerPos, metaEntry.headerLen));
        if ((metaEntry.flags & MetaEntry.UPDATE_ENTRY) != 0) {
	        return new DocumentUpdate(buf);
        } else if ((metaEntry.flags & MetaEntry.REMOVE_ENTRY) != 0) {
            return new DocumentRemove(new Document(buf).getId());
	    } else {
            DocumentPut op = new DocumentPut(new Document(buf));
            op.getDocument().setLastModified(getTimestamp());
            return op;
        }
    }

}
