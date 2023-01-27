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
 * A 16-bit float field value
 *
 * @author bratseth
 */
public final class Float16FieldValue extends NumericFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new Float16FieldValue(); }
        @Override public FieldValue create(String value) { return new Float16FieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 18, Float16FieldValue.class);
    private float value; // 16-bit not supported in Java yet

    public Float16FieldValue() {
        this((float) 0);
    }

    public Float16FieldValue(float value) {
        this.value = value;
    }

    public Float16FieldValue(Float value) {
        this.value = value;
    }

    public Float16FieldValue(String s) { value = Float.parseFloat(s); }

    @Override
    public Float16FieldValue clone() {
        Float16FieldValue val = (Float16FieldValue) super.clone();
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
        if (!checkAssign(obj)) return;

        if (obj instanceof Number)
            value = ((Number) obj).floatValue();
        else if (obj instanceof NumericFieldValue)
            value = (((NumericFieldValue) obj).getNumber().floatValue());
        else if (obj instanceof String || obj instanceof StringFieldValue)
            value = Float.parseFloat(obj.toString());
        else
            throw new IllegalArgumentException("Class " + obj.getClass() + " not applicable to an " + this.getClass() + " instance.");
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
        return DataType.FLOAT16;
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printShortfloatXml(this, xml);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
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
        if (!(o instanceof Float16FieldValue)) return false;
        if (!super.equals(o)) return false;

        Float16FieldValue that = (Float16FieldValue) o;
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
        if (comp != 0) return comp;
        return Float.compare(value, ((Float16FieldValue) fieldValue).value);
    }

}
