// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;

/**
 * <p>Value update that represents assigning a new value.</p>
 *
 * @author Einar M R Rosenvinge
 */
public class AssignValueUpdate extends ValueUpdate {

    protected FieldValue value;

    public AssignValueUpdate(FieldValue value) {
        super(ValueUpdateClassID.ASSIGN);
        this.value = value;
    }

    /**
     * <p>Returns the value of this value update.</p>
     *
     * <p>The type of the value is defined by the type of this field
     * in this documents DocumentType - a java.lang primitive wrapper for single value types,
     * java.util.List for arrays and {@link com.yahoo.document.datatypes.WeightedSet WeightedSet}
     * for weighted sets.</p>
     *
     * @return the value of this ValueUpdate
     * @see com.yahoo.document.DataType
     */
    public FieldValue getValue() { return value; }

    /**
     * <p>Sets the value to assign.</p>
     *
     * <p>The type of the value must match the type of this field
     * in this documents DocumentType - a java.lang primitive wrapper for single value types,
     * java.util.List for arrays and {@link com.yahoo.document.datatypes.WeightedSet WeightedSet}
     * for weighted sets.</p>
     */
    public void setValue(FieldValue value) { this.value=value; }

    @Override
    public FieldValue applyTo(FieldValue fval) {
        if (value == null) return null;
        fval.assign(value);
        return fval;
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
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
        boolean baseEquals = o instanceof AssignValueUpdate && super.equals(o);

        if (!baseEquals) return false;

        if (value == null && ((AssignValueUpdate) o).value == null) {
            return true;
        } else if (value != null && value.equals(((AssignValueUpdate) o).value)) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (value == null ? 0 : value.hashCode());
    }

    @Override
    public String toString() {
        return super.toString() + " " + value;
    }

}
