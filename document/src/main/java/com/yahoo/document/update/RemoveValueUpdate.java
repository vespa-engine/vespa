// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;

/**
 * <p>Value update representing a removal of a value (and its associated weight, if any)
 * from a multi-valued data type.</p>
 * Deprecated: Use RemoveFieldPathUpdate instead.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class RemoveValueUpdate extends ValueUpdate {
    protected FieldValue value;

    public RemoveValueUpdate(FieldValue value) {
        super(ValueUpdateClassID.REMOVE);
        this.value = value;
    }

    /** Sets the key this should remove */
    public FieldValue getValue() { return value; }

    public void setValue(FieldValue value) { this.value=value; }

    @Override
    public FieldValue applyTo(FieldValue fval) {
        if (fval instanceof CollectionFieldValue) {
            CollectionFieldValue val = (CollectionFieldValue) fval;
            val.removeValue(value);
        }
        return fval;
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
        if (!(fieldType instanceof CollectionDataType)) {
            throw new UnsupportedOperationException("Expected collection, got " + fieldType.getName() + ".");
        }
        fieldType = ((CollectionDataType)fieldType).getNestedType();
        if (value != null && !value.getDataType().equals(fieldType)) {
            throw new IllegalArgumentException("Expected " + fieldType.getName() + ", got " +
                                               value.getDataType().getName());
        }
    }

    @Override
    public void serialize(DocumentUpdateWriter data, DataType superType) {
        data.write(this, superType);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RemoveValueUpdate && super.equals(o) && value.equals(((RemoveValueUpdate) o).value);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + value.hashCode();
    }

    @Override
    public String toString() {
        return super.toString() + " " + value;
    }
}
