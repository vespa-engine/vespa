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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;

/**
 * A field value which is an array of byte data
 *
 * @author Einar M R Rosenvinge
 */
public final class Raw extends FieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new Raw(); }
        @Override public FieldValue create(String value) { return new Raw(Base64.getMimeDecoder().decode(value)); }
    }

    public static final int classId = registerClass(Ids.document + 16, Raw.class);
    private ByteBuffer value;

    public Raw() {
        value = null;
    }

    public Raw(ByteBuffer value) {
        this.value = value;
    }

    public Raw(byte [] buf) {
        this.value = ByteBuffer.wrap(buf);
        value.position(0);
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }

    public ByteBuffer getByteBuffer() {
        return value;
    }

    @Override
    public Raw clone() {
        Raw raw = (Raw) super.clone();
        if (value.hasArray()) {
            raw.value = ByteBuffer.wrap(Arrays.copyOf(value.array(), value.array().length));
            raw.value.position(value.position());
        } else {
            byte[] copyBuf = new byte[value.capacity()];
            int origPos = value.position();
            value.position(0);
            value.get(copyBuf);
            value.position(origPos);
            raw.value = ByteBuffer.wrap(copyBuf);
            raw.value.position(value.position());
        }
        return raw;
    }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    public void clear() {
        value = ByteBuffer.wrap(new byte[0]);
    }

    @Override
    public void assign(Object o) {
        if (!checkAssign(o)) {
            return;
        }
        if (o instanceof Raw) {
            value = ((Raw) o).value;
        } else if (o instanceof ByteBuffer) {
            value = (ByteBuffer) o;
        } else if (o instanceof byte[]) {
            ByteBuffer byteBufVal = ByteBuffer.wrap((byte[]) o);
            byteBufVal.position(0);
            value = byteBufVal;
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    @Override
    public DataType getDataType() {
        return DataType.RAW;
    }

    @Override
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printRawXml(this, xml);
    }

    @Override
    public String toString() {
        ByteBuffer buf = value.slice();
        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        return com.yahoo.io.HexDump.toHexString(arr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Raw)) return false;
        if (!super.equals(o)) return false;

        Raw raw = (Raw) o;

        if (value != null ? !value.equals(raw.value) : raw.value != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
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

}
