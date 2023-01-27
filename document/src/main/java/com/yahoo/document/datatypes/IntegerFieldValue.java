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
 * A 32-bit integer field value
 *
 * @author Einar M R Rosenvinge
 */
public final class IntegerFieldValue extends NumericFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new IntegerFieldValue(); }
        @Override public FieldValue create(String value) { return new IntegerFieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 11, IntegerFieldValue.class);
    private int value;

    public IntegerFieldValue() {
        this(0);
    }

    public IntegerFieldValue(int value) {
        this.value = value;
    }

    public IntegerFieldValue(Integer value) {
        this.value = value;
    }

    public IntegerFieldValue(String s) {
        value = Integer.parseInt(s);
    }

    @Override
    public IntegerFieldValue clone() {
        IntegerFieldValue val = (IntegerFieldValue) super.clone();
        val.value = value;
        return val;
    }

    @Override
    public Number getNumber() {
        return value;
    }

    @Override
    public void clear() {
        value = 0;
    }

    @Override
    public void assign(Object obj) {
        if (!checkAssign(obj)) {
            return;
        }
        if (obj instanceof Number) {
            value = ((Number) obj).intValue();
        } else if (obj instanceof NumericFieldValue) {
            value = (((NumericFieldValue) obj).getNumber().intValue());
        } else if (obj instanceof String || obj instanceof StringFieldValue) {
            value = Integer.parseInt(obj.toString());
        } else {
            throw new IllegalArgumentException("Class " + obj.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    public int getInteger() {
        return value;
    }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printIntegerXml(this, xml);
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + value;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegerFieldValue)) return false;
        if (!super.equals(o)) return false;

        IntegerFieldValue that = (IntegerFieldValue) o;
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
    public DataType getDataType() {
        return DataType.INT;
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        //types are equal, this must be of this type
        IntegerFieldValue otherValue = (IntegerFieldValue) fieldValue;
        if (value < otherValue.value) {
            return -1;
        } else if (value > otherValue.value) {
            return 1;
        } else {
            return 0;
        }
    }

}
