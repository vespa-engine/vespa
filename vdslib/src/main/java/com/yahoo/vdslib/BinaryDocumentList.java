// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespa.objects.Serializer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class BinaryDocumentList extends DocumentList {

    private DocumentTypeManager docMan;
    private byte[] buffer;
    private int docCount;

    /**
     * Create a new documentlist, using the given buffer.
     *
     * @param buffer buffer containing documents
     */
    BinaryDocumentList(DocumentTypeManager docMan, byte[] buffer) {
        this.docMan = docMan;
        ByteBuffer buf = ByteBuffer.wrap(buffer);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        docCount = buf.getInt();
        this.buffer = buffer;

    }

    @Override
    public Entry get(int index) throws ArrayIndexOutOfBoundsException {
        if (index < docCount) {
            return Entry.create(docMan, buffer, index);
        } else {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + docCount);
        }
    }

    @Override
    public int size() { return docCount; }

    @Override
    public int getApproxByteSize() {
        return buffer.length;
    }

    @Override
    public void serialize(Serializer buf) {
        buf.put(null, buffer);
    }

}
