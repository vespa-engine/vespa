// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.vespa.objects.FieldBase;

/**
 * Class used for serializing documents on the current head document format.
 *
 * @author baldersheim
 */
@SuppressWarnings("deprecation")
public class VespaDocumentSerializerHead extends VespaDocumentSerializer42 {

    public VespaDocumentSerializerHead(GrowableByteBuffer buf) {
        super(buf);
    }

    @Override
    public void write(DocumentUpdate update) {
        update.getId().serialize(this);

        update.getDocumentType().serialize(this);

        putInt(null, update.fieldUpdates().size());

        for (FieldUpdate up : update.fieldUpdates()) {
            up.serialize(this);
        }

        DocumentUpdateFlags flags = new DocumentUpdateFlags();
        flags.setCreateIfNonExistent(update.getCreateIfNonExistent());
        putInt(null, flags.injectInto(update.fieldPathUpdates().size()));

        for (FieldPathUpdate up : update.fieldPathUpdates()) {
            up.serialize(this);
        }
    }

    public void write(FieldPathUpdate update) {
        putByte(null, (byte)update.getUpdateType().getCode());
        put(null, update.getOriginalFieldPath());
        put(null, update.getOriginalWhereClause());
    }

    public void write(AssignFieldPathUpdate update) {
        write((FieldPathUpdate)update);
        byte flags = 0;
        if (update.getRemoveIfZero()) {
            flags |= AssignFieldPathUpdate.REMOVE_IF_ZERO;
        }
        if (update.getCreateMissingPath()) {
            flags |= AssignFieldPathUpdate.CREATE_MISSING_PATH;
        }
        if (update.isArithmetic()) {
            flags |= AssignFieldPathUpdate.ARITHMETIC_EXPRESSION;
            putByte(null, flags);
            put(null, update.getExpression());
        } else {
            putByte(null, flags);
            update.getFieldValue().serialize(this);
        }
    }

    public void write(AddFieldPathUpdate update) {
        write((FieldPathUpdate)update);
        update.getNewValues().serialize(this);
    }

    @Override
    public void write(FieldBase field, ByteFieldValue value) {
        buf.put(value.getByte());
    }
}
