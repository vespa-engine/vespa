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
 * A boolean field value
 *
 * @author bratseth
 */
public class BoolFieldValue extends FieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new BoolFieldValue(); }
        @Override public FieldValue create(String value) { return new BoolFieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 17, BoolFieldValue.class);
    private boolean value;

    public BoolFieldValue() {
        this(false);
    }

    public BoolFieldValue(boolean value) {
        this.value = value;
    }

    public BoolFieldValue(String s) { value = Boolean.parseBoolean(s); }

    @Override
    public BoolFieldValue clone() {
        return (BoolFieldValue)super.clone();
    }

    @Override
    public void clear() {
        value = false;
    }

    @Override
    public void assign(Object o) {
        if ( ! checkAssign(o)) return;
        if (o instanceof String || o instanceof StringFieldValue) {
            value = Boolean.parseBoolean(o.toString());
        } else if (o instanceof Boolean) {
            value = (Boolean) o;
        } else if (o instanceof BoolFieldValue) {
            value = ((BoolFieldValue) o).value;
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    public boolean getBoolean() {
        return value;
    }
    public void setBoolean(boolean value) { this.value = value; }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    public DataType getDataType() {
        return DataType.BOOL;
    }

    @Override
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printBoolXml(this, xml);
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + ( value ? 3 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof BoolFieldValue)) return false;
        if ( ! super.equals(o)) return false;

        BoolFieldValue that = (BoolFieldValue) o;
        return (value == that.value);
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
    public int compareTo(FieldValue other) {
        int comp = super.compareTo(other);
        if (comp != 0) return comp;
        return Boolean.compare(value, ((BoolFieldValue)other).value);
    }

}
