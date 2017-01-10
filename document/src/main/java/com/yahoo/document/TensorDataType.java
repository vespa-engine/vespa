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
        super(tensorType.toString(), 0);
        this.tensorType = tensorType;
        setId(getName().toLowerCase().hashCode());
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
        return value != null && TensorFieldValue.class.isAssignableFrom(value.getClass());
    }

    /** Returns the type of the tensor this field can hold */
    public TensorType getTensorType() { return tensorType; }
    
}
