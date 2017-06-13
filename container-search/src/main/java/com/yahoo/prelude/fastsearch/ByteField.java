// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a byte field in the result set
 *
 */

package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;

/**
 * @author  <a href="mailto:borud@yahoo-inc.com">Bj\u00f8rn Borud</a>
 */
public class ByteField extends DocsumField {
    static final byte EMPTY_VALUE = Byte.MIN_VALUE;

    public ByteField(String name) {
        super(name);
    }

    private Object convert(byte value) {
        if (value == EMPTY_VALUE) {
            return NanNumber.NaN;
        } else {
            return Byte.valueOf(value);
        }
    }

    public Object decode(ByteBuffer b) {
        return convert(b.get());
    }

    public Object decode(ByteBuffer b, FastHit hit) {
        Object field = decode(b);
        hit.setField(name, field);
        return field;
    }

    public int getLength(ByteBuffer b) {
        int offset = b.position();
        final int bytelength = Byte.SIZE >> 3;
        b.position(offset + bytelength);
        return bytelength;
    }

    public Object convert(Inspector value) {
        return convert((byte)value.asLong(EMPTY_VALUE));
    }
}
