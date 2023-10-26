// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentUpdateWriter;

/**
 * <p>Value update that represents performing an encapsulated value update on a subvalue. Currently, there are
 * two multi-value data types in Vespa, <em>array</em> and <em>weighted set</em>. </p>
 *
 * <ul>
 * <li>For an array, the value
 * must be an Integer, and the update must represent a legal operation on the subtype of the array. </li>
 * <li>For a
 * weighted set, the value must be a key of the same type as the subtype of the weighted set, and the update
 * must represent a legal operation on an integer value.</li>
 * </ul>
 *
 * @author Einar M R Rosenvinge
 */
public class MapValueUpdate extends ValueUpdate {
    protected FieldValue value;
    protected ValueUpdate update;

    public MapValueUpdate(FieldValue value, ValueUpdate update) {
        super(ValueUpdateClassID.MAP);
        this.value = value;
        this.update = update;
    }

    /** Returns the key of the nested update */
    public FieldValue getValue() {
        return value;
    }

    /** Sets the key of the nested update */
    public void setValue(FieldValue value) {
        this.value=value;
    }

    public ValueUpdate getUpdate() {
        return update;
    }

    @Override
    public FieldValue applyTo(FieldValue fval) {
        if (fval instanceof Array) {
            Array array = (Array) fval;
            FieldValue element = array.getFieldValue(((IntegerFieldValue) value).getInteger());
            element = update.applyTo(element);
            array.set(((IntegerFieldValue) value).getInteger(), element);
        } else if (fval instanceof WeightedSet) {
            WeightedSet wset = (WeightedSet) fval;
            WeightedSetDataType wtype = wset.getDataType();
            Integer weight = wset.get(value);
            if (weight == null) {
                if (wtype.createIfNonExistent() && update instanceof ArithmeticValueUpdate) {
                    weight = 0;
                } else {
                    return fval;
                }
            }
            weight = (Integer) update.applyTo(new IntegerFieldValue(weight)).getWrappedValue();
            wset.put(value, weight);
            if (wtype.removeIfZero() && update instanceof ArithmeticValueUpdate && weight == 0) {
                wset.remove(value);
            }
        }
        return fval;
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
        if (fieldType instanceof ArrayDataType) {
            if (!(value instanceof IntegerFieldValue)) {
                throw new IllegalArgumentException("Expected integer, got " + value.getClass().getName() + ".");
            }
            update.checkCompatibility(((ArrayDataType)fieldType).getNestedType());
        } else if (fieldType instanceof WeightedSetDataType) {
            ((WeightedSetDataType)fieldType).getNestedType().createFieldValue().assign(value);
            update.checkCompatibility(DataType.INT);
        } else if (fieldType instanceof StructuredDataType) {
            if (!(value instanceof StringFieldValue)) {
                throw new IllegalArgumentException("Expected string, got " + value.getClass().getName() + ".");
            }
            Field field = ((StructuredDataType)fieldType).getField(((StringFieldValue)value).getString());
            if (field == null) {
                throw new IllegalArgumentException("Field '" + value + "' not found.");
            }
            update.checkCompatibility(field.getDataType());
        } else {
            throw new UnsupportedOperationException("Field type " + fieldType.getName() + " not supported.");
        }
    }


    @Override
    public void serialize(DocumentUpdateWriter data, DataType superType) {
        data.write(this, superType);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MapValueUpdate && super.equals(o) && value.equals(((MapValueUpdate) o).value) &&
                update.equals(((MapValueUpdate) o).update);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + value.hashCode() + update.hashCode();
    }

    @Override
    public String toString() {
        return super.toString() + " " + value + " " + update;
    }

}

