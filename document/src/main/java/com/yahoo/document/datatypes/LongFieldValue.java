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
 * A 64-bit integer field value
 *
 * @author Einar M R Rosenvinge
 */
public final class LongFieldValue extends NumericFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new LongFieldValue(); }
        @Override public FieldValue create(String value) { return new LongFieldValue(value); }
    }
    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 12, LongFieldValue.class);
    private long value;

    public LongFieldValue() {
        this(0l);
    }

    public LongFieldValue(long value) {
        this.value = value;
    }

    public LongFieldValue(Long value) {
        this.value = value;
    }

    public LongFieldValue(String s) {
        value = Long.parseLong(s);
    }

    @Override
    public LongFieldValue clone() {
        LongFieldValue val = (LongFieldValue) super.clone();
        val.value = value;
        return val;
    }

    @Override
    public void clear() {
        value = 0l;
    }

    @Override
    public Number getNumber() {
        return value;
    }

    @Override
    public void assign(Object obj) {
        if (!checkAssign(obj)) {
            return;
        }
        if (obj instanceof Number) {
            value = ((Number) obj).longValue();
        } else if (obj instanceof NumericFieldValue) {
            value = (((NumericFieldValue) obj).getNumber().longValue());
        } else if (obj instanceof String || obj instanceof StringFieldValue) {
            value = Long.parseLong(obj.toString());
        } else {
            throw new IllegalArgumentException("Class " + obj.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    public long getLong() {
        return value;
    }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    public DataType getDataType() {
        return DataType.LONG;
    }

    @Override
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printLongXml(this, xml);
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (value ^ (value >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongFieldValue)) return false;
        if (!super.equals(o)) return false;

        LongFieldValue that = (LongFieldValue) o;
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
        LongFieldValue otherValue = (LongFieldValue) fieldValue;
        if (value < otherValue.value) {
            return -1;
        } else if (value > otherValue.value) {
            return 1;
        } else {
            return 0;
        }
    }

}
