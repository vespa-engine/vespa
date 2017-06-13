// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a long data field in the result set.
 *
 */
package com.yahoo.prelude.fastsearch;

import java.nio.ByteBuffer;

import com.yahoo.io.SlowInflate;
import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.Inspector;

/**
 * @author Bjørn Borud
 */
public class LongdataField extends DocsumField implements VariableLengthField {

    public LongdataField(String name) {
        super(name);
    }

    private Object convert(byte[] value) {
        return new RawData(value);
    }

    @Override
    public Object decode(ByteBuffer b) {
        long dataLen = 0;
        long len = ((long) b.getInt()) & 0xffffffffL;
        boolean compressed;

        // if MSB is set this is a compressed field.  set the compressed
        // flag accordingly and decompress the data
        compressed = ((len & 0x80000000) != 0);
        if (compressed) {
            len &= 0x7fffffff;
            dataLen = b.getInt();
            len -= 4;
        }

        byte[] tmp = new byte[(int) len];

        b.get(tmp);

        if (compressed) {
            SlowInflate inf = new SlowInflate();

            tmp = inf.unpack(tmp, (int) dataLen);
        }
        return convert(tmp);
    }

    @Override
    public Object decode(ByteBuffer b, FastHit hit) {
        Object field = decode(b);
        hit.setField(name, field);
        return field;
    }

    @Override
    public int getLength(ByteBuffer b) {
        int offset = b.position();
        // MSB = compression flag, re decode
        int len = b.getInt() & 0x7fffffff;
        b.position(offset + len + (Integer.SIZE >> 3));
        return len + (Integer.SIZE >> 3);
    }

    @Override
    public boolean isCompressed(ByteBuffer b) {
        int offset = b.position();
        // MSB = compression flag, re decode
        int compressed = b.getInt() & 0x80000000;
        b.position(offset);
        return compressed != 0;
    }

    @Override
    public int sizeOfLength() {
        return Integer.SIZE >> 3;
    }

    @Override
    public Object convert(Inspector value) {
        return convert(value.asData(Value.empty().asData()));
    }
}
