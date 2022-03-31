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
 * A byte field value
 *
 * @author Einar M R Rosenvinge
 */
public class ByteFieldValue extends NumericFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new ByteFieldValue(); }
        @Override public FieldValue create(String value) { return new ByteFieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 10, ByteFieldValue.class);
    private byte value;

    public ByteFieldValue() {
        this((byte) 0);
    }

    public ByteFieldValue(byte value) {
        this.value = value;
    }

    public ByteFieldValue(Byte value) {
        this.value = value;
    }

    public ByteFieldValue(Integer value) {
        this.value = (byte) value.intValue();
    }

    public ByteFieldValue(String s) { value = Byte.parseByte(s); }

    @Override
    public ByteFieldValue clone() {
        ByteFieldValue val = (ByteFieldValue) super.clone();
        val.value = value;
        return val;

    }

    @Override
    public Number getNumber() {
        return value;
    }

    @Override
    public void clear() {
        value = (byte) 0;
    }

    @Override
    public void assign(Object o) {
        if (!checkAssign(o)) {
            return;
        }
        if (o instanceof Number) {
            value = ((Number) o).byteValue();
        } else if (o instanceof NumericFieldValue) {
            value = ((NumericFieldValue) o).getNumber().byteValue();
        } else if (o instanceof String || o instanceof StringFieldValue) {
            value = Byte.parseByte(o.toString());
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    public byte getByte() {
        return value;
    }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    public DataType getDataType() {
        return DataType.BYTE;
    }

    @Override
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printByteXml(this, xml);
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) value;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteFieldValue)) return false;
        if (!super.equals(o)) return false;

        ByteFieldValue that = (ByteFieldValue) o;
        if (value != that.value) return false;
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
        ByteFieldValue otherValue = (ByteFieldValue) fieldValue;
        if (value < otherValue.value) {
            return -1;
        } else if (value > otherValue.value) {
            return 1;
        } else {
            return 0;
        }
    }

}
