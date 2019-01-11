// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.vespa.objects.FieldBase;

/**
 * Class used for de-serializing documents on the current head document format.
 *
 * @author baldersheim
 */
@SuppressWarnings("deprecation")
public class VespaDocumentDeserializerHead extends VespaDocumentDeserializer42 {

    public VespaDocumentDeserializerHead(DocumentTypeManager manager, GrowableByteBuffer buffer) {
        super(manager, buffer);
    }

    @Override
    public void read(DocumentUpdate update) {
        update.setId(new DocumentId(this));
        update.setDocumentType(readDocumentType());

        int size = getInt(null);

        for (int i = 0; i < size; i++) {
            update.addFieldUpdate(new FieldUpdate(this, update.getDocumentType(), 8));
        }

        int sizeAndFlags = getInt(null);
        update.setCreateIfNonExistent(DocumentUpdateFlags.extractFlags(sizeAndFlags).getCreateIfNonExistent());
        size = DocumentUpdateFlags.extractValue(sizeAndFlags);

        for (int i = 0; i < size; i++) {
            int type = getByte(null);
            update.addFieldPathUpdate(FieldPathUpdate.create(FieldPathUpdate.Type.valueOf(type),
                                      update.getDocumentType(), this));
        }
    }

    @Override
    public void read(FieldBase field, BoolFieldValue value) {
        value.setBoolean((getByte(null) != 0));
    }
}
