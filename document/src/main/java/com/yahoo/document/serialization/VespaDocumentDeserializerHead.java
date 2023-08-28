// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.TensorType;

/**
 * Class used for de-serializing documents on the current head document format.
 *
 * @author baldersheim
 */
public class VespaDocumentDeserializerHead extends VespaDocumentDeserializer6 {

    public VespaDocumentDeserializerHead(DocumentTypeManager manager, GrowableByteBuffer buffer) {
        super(manager, buffer);
    }

    @Override
    protected ValueUpdate readTensorModifyUpdate(DataType type) {
        byte operationId = getByte(null);
        var operation = decodeOperation(operationId);
        if (operation == null) {
            throw new DeserializationException("Unknown operation id " + operationId + " for tensor modify update");
        }
        if (!(type instanceof TensorDataType)) {
            throw new DeserializationException("Expected tensor data type, got " + type);
        }
        var createNonExistingCells = decodeCreateNonExistingCells(operationId);
        if (createNonExistingCells) {
            // Read the default cell value (but it is not used by TensorModifyUpdate).
            getDouble(null);
        }
        TensorDataType tensorDataType = (TensorDataType)type;
        TensorType tensorType = tensorDataType.getTensorType();
        TensorType convertedType = TensorModifyUpdate.convertDimensionsToMapped(tensorType);

        TensorFieldValue tensor = new TensorFieldValue(convertedType);
        tensor.deserialize(this);
        return new TensorModifyUpdate(operation, tensor, createNonExistingCells);
    }

    private TensorModifyUpdate.Operation decodeOperation(byte operationId) {
        byte OP_MASK = 0b01111111;
        return TensorModifyUpdate.Operation.getOperation(operationId & OP_MASK);
    }

    private boolean decodeCreateNonExistingCells(byte operationId) {
        byte CREATE_FLAG = -0b10000000;
        return (operationId & CREATE_FLAG) != 0;
    }

    @Override
    protected ValueUpdate readTensorAddUpdate(DataType type) {
        if (!(type instanceof TensorDataType)) {
            throw new DeserializationException("Expected tensor data type, got " + type);
        }
        TensorDataType tensorDataType = (TensorDataType)type;
        TensorType tensorType = tensorDataType.getTensorType();
        TensorFieldValue tensor = new TensorFieldValue(tensorType);
        tensor.deserialize(this);
        return new TensorAddUpdate(tensor);
    }

    @Override
    protected ValueUpdate readTensorRemoveUpdate(DataType type) {
        if (!(type instanceof TensorDataType)) {
            throw new DeserializationException("Expected tensor data type, got " + type);
        }
        TensorDataType tensorDataType = (TensorDataType)type;
        TensorType tensorType = tensorDataType.getTensorType();

        TensorFieldValue tensor = new TensorFieldValue();
        tensor.deserialize(this);
        var result = new TensorRemoveUpdate(tensor);
        result.verifyCompatibleType(tensorType);
        return result;
    }

}
