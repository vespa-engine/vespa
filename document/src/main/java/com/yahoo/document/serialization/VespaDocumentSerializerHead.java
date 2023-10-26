// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.io.GrowableByteBuffer;

/**
 * Class used for serializing documents on the current head document format.
 *
 * @author baldersheim
 */
public class VespaDocumentSerializerHead extends VespaDocumentSerializer6 {

    public VespaDocumentSerializerHead(GrowableByteBuffer buf) {
        super(buf);
    }

    @Override
    public void write(TensorModifyUpdate update) {
        putByte(null, (byte) encodeOperationId(update));
        if (update.getCreateNonExistingCells()) {
            putDouble(null, update.getDefaultCellValue());
        }
        update.getValue().serialize(this);
    }

    private int encodeOperationId(TensorModifyUpdate update) {
        int operationId = update.getOperation().id;
        byte CREATE_FLAG = -0b10000000;
        if (update.getCreateNonExistingCells()) {
            operationId |= CREATE_FLAG;
        }
        return operationId;
    }

    @Override
    public void write(TensorAddUpdate update) {
        update.getValue().serialize(this);
    }

    @Override
    public void write(TensorRemoveUpdate update) {
        update.getValue().serialize(this);
    }


}
