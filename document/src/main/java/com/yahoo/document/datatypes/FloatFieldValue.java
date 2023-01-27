// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.objects.Ids;

/**
 * A 32-bit float field value
 *
 * @author Einar M R Rosenvinge
 */
public final class FloatFieldValue extends NumericFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new FloatFieldValue(); }
        @Override public FieldValue create(String value) { return new FloatFieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 13, FloatFieldValue.class);
    private float value;

    public FloatFieldValue() {
        this((float) 0);
    }

    public FloatFieldValue(float value) {
        this.value = value;
    }

    public FloatFieldValue(Float value) {
        this.value = value;
    }

    public FloatFieldValue(String s) { value = Float.parseFloat(s); }

    @Override
    public FloatFieldValue clone() {
        FloatFieldValue val = (FloatFieldValue) super.clone();
        val.value = value;
        return val;
    }

    @Override
    public Number getNumber() {
        return value;
    }

    @Override
    public void clear() {
        value = 0.0f;
    }

    @Override
    public void assign(Object obj) {
        if (!checkAssign(obj)) {
            return;
        }
        if (obj instanceof Number) {
            value = ((Number) obj).floatValue();
        } else if (obj instanceof NumericFieldValue) {
            value = (((NumericFieldValue) obj).getNumber().floatValue());
        } else if (obj instanceof String || obj instanceof StringFieldValue) {
            value = Float.parseFloat(obj.toString());
        } else {
            throw new IllegalArgumentException("Class " + obj.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    public float getFloat() {
        return value;
    }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    public DataType getDataType() {
        return DataType.FLOAT;
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printFloatXml(this, xml);
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (value != +0.0f ? Float.floatToIntBits(value) : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FloatFieldValue)) return false;
        if (!super.equals(o)) return false;

        FloatFieldValue that = (FloatFieldValue) o;
        if (Float.compare(that.value, value) != 0) return false;
        return true;
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    /* (non-Javadoc)
     * @see com.yahoo.document.datatypes.FieldValue#deserialize(com.yahoo.document.Field, com.yahoo.document.serialization.FieldReader)
     */
    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        //types are equal, this must be of this type
        FloatFieldValue otherValue = (FloatFieldValue) fieldValue;
        return Float.compare(value, otherValue.value);
    }

}
