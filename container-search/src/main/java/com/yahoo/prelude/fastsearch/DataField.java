// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a data field in the result set.  a data field
 * is basically the same thing as a string field, only that we
 * treat it like a raw buffer.  Well we SHOULD.  we don't actually
 * do so.  yet.  we should probably do some defensive copying and
 * return a ByteBuffer...hmm...
 *
 */

package com.yahoo.prelude.fastsearch;

import java.nio.ByteBuffer;

import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.Inspector;

/**
 * @author Bjørn Borud
 */
public class DataField extends DocsumField implements VariableLengthField {

    public DataField(String name) {
        super(name);
    }

    private Object convert(byte[] value) {
        return new RawData(value);
    }

    @Override
    public Object decode(ByteBuffer b) {
        int len = ((int) b.getShort()) & 0xffff;

        byte[] tmp = new byte[len];
        b.get(tmp);
        return convert(tmp);
    }

    @Override
    public Object decode(ByteBuffer b, FastHit hit) {
        Object field = decode(b);
        hit.setField(name, field);
        return field;
    }

    @Override
    public String toString() {
        return "field " + getName() + " type data";
    }

    @Override
    public int getLength(ByteBuffer b) {
        int offset = b.position();
        int len = ((int) b.getShort()) & 0xffff;
        b.position(offset + len + (Short.SIZE >> 3));
        return len + (Short.SIZE >> 3);
    }

    @Override
    public int sizeOfLength() {
        return Short.SIZE >> 3;
    }

    @Override
    public Object convert(Inspector value) {
        return convert(value.asData(Value.empty().asData()));
    }

}
