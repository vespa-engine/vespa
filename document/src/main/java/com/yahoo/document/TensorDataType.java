// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.objects.Ids;

/**
 * A DataType containing a tensor type
 *
 * @author bratseth
 */
public class TensorDataType extends DataType {

    private final TensorType tensorType;

    // The global class identifier shared with C++.
    public static int classId = registerClass(Ids.document + 59, TensorDataType.class);

    public TensorDataType(TensorType tensorType) {
        super(tensorType.toString(), DataType.tensorDataTypeCode);
        this.tensorType = tensorType;
    }

    public TensorDataType clone() {
        return (TensorDataType)super.clone();
    }

    @Override
    public FieldValue createFieldValue() {
        return new TensorFieldValue(tensorType);
    }

    @Override
    public Class<? extends TensorFieldValue> getValueClass() {
        return TensorFieldValue.class;
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        if (value == null) return false;
        if ( ! TensorFieldValue.class.isAssignableFrom(value.getClass())) return false;
        TensorFieldValue tensorValue = (TensorFieldValue)value;
        // TODO: Change to isConvertibleTo when tensor attribute reloading on reconfig is implemented
        return tensorType.isAssignableTo(tensorValue.getDataType().getTensorType());
    }

    /** Returns the type of the tensor this field can hold */
    public TensorType getTensorType() { return tensorType; }

}
