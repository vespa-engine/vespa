// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentUpdateWriter;

/**
 * <p>Value update representing an addition of a value (possibly with an associated weight)
 * to a multi-valued data type.</p>
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class AddValueUpdate extends ValueUpdate {
    protected FieldValue value;
    protected Integer weight;

    AddValueUpdate(FieldValue value) {
        super(ValueUpdateClassID.ADD);
        setValue(value, 1);
    }

    public AddValueUpdate(FieldValue key, int weight) {
        super(ValueUpdateClassID.ADD);
        setValue(key, weight);
    }

    private void setValue(FieldValue key, int weight) {
        this.value = key;
        this.weight = weight;
    }

    /**
     * Returns the value of this value update.
     *
     * @return the value of this ValueUpdate
     * @see com.yahoo.document.DataType
     */
    public FieldValue getValue() { return value; }

    public void setValue(FieldValue value) { this.value=value; }

    /**
     * Return the associated weight of this value update.
     *
     * @return the weight of this value update, or 1 if unset
     */
    public int getWeight() {
        return weight;
    }

    @Override
    public FieldValue applyTo(FieldValue val) {
        if (val instanceof WeightedSet) {
            WeightedSet wset = (WeightedSet) val;
            wset.put(value, weight);
        } else if (val instanceof CollectionFieldValue) {
            CollectionFieldValue fval = (CollectionFieldValue) val;
            fval.add(value);
        } else {
            throw new IllegalStateException("Cannot add "+value+" to field of type " + val.getClass().getName());
        }
        return val;
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
        return o instanceof AddValueUpdate && super.equals(o) && value.equals(((AddValueUpdate) o).value) &&
                weight.equals(((AddValueUpdate) o).weight);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + value.hashCode() + weight;
    }

    @Override
    public String toString() {
        return super.toString() + " " + value + " " + weight;
    }

}
