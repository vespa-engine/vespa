// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        TensorModifyUpdate.Operation operation = TensorModifyUpdate.Operation.getOperation(operationId);
        if (operation == null) {
            throw new DeserializationException("Unknown operation id " + operationId + " for tensor modify update");
        }
        if (!(type instanceof TensorDataType)) {
            throw new DeserializationException("Expected tensor data type, got " + type);
        }
        TensorDataType tensorDataType = (TensorDataType)type;
        TensorType tensorType = tensorDataType.getTensorType();
        TensorType convertedType = TensorModifyUpdate.convertDimensionsToMapped(tensorType);

        TensorFieldValue tensor = new TensorFieldValue(convertedType);
        tensor.deserialize(this);
        return new TensorModifyUpdate(operation, tensor);
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
