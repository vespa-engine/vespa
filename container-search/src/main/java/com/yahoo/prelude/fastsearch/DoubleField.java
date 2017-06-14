// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;

/**
 * @author <a href="mailto:mathiasm@yahoo-inc.com">Mathias M\u00f8lster Lidal</a>
 */
public class DoubleField extends DocsumField {
    static final double EMPTY_VALUE = Double.NaN;

    public DoubleField(String name) {
        super(name);
    }

    private Object convert(double value) {
        if (Double.isNaN(value)) {
            return NanNumber.NaN;
        } else {
            return Double.valueOf(value);
        }
    }

    public Object decode(ByteBuffer b) {
        return convert(b.getDouble());
    }

    public Object decode(ByteBuffer b, FastHit hit) {
        Object field = decode(b);
        hit.setField(name, field);
        return field;
    }

    public int getLength(ByteBuffer b) {
        int offset = b.position();
        final int byteLength = Double.SIZE >> 3;
        b.position(offset + byteLength);
        return byteLength;
    }

    public Object convert(Inspector value) {
        return convert(value.asDouble(EMPTY_VALUE));
    }
}
