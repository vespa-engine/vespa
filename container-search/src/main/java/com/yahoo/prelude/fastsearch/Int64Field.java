// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a integer field in the result set
 *
 */
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;


/**
 * @author  <a href="mailto:borud@yahoo-inc.com">Bj\u00f8rn Borud</a>
 */
public class Int64Field extends DocsumField {
    static final long EMPTY_VALUE = Long.MIN_VALUE;

    public Int64Field(String name) {
        super(name);
    }

    private Object convert(long value) {
        if (value == EMPTY_VALUE) {
            return NanNumber.NaN;
        } else {
            return Long.valueOf(value);
        }
    }

    public Object decode(ByteBuffer b) {
        return convert(b.getLong());
    }

    public Object decode(ByteBuffer b, FastHit hit) {
        Object field = decode(b);
        hit.setField(name, field);
        return field;
    }

    public String toString() {
        return "field " + getName() + " type int64";
    }

    public int getLength(ByteBuffer b) {
        int offset = b.position();
        final int bytelength = Long.SIZE >> 3;
        b.position(offset + bytelength);
        return bytelength;
    }

    public Object convert(Inspector value) {
        return convert(value.asLong(EMPTY_VALUE));
    }
}
