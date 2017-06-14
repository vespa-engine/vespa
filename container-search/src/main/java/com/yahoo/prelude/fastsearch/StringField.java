// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a string field in the result set
 *
 */
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.text.Utf8;
import com.yahoo.data.access.Inspector;


/**
 * @author Bjørn Borud
 */
public class StringField extends DocsumField implements VariableLengthField {

    public StringField(String name) {
        super(name);
    }

    @Override
    public Object decode(ByteBuffer b) {
        int length = ((int) b.getShort()) & 0xffff;
        Object field;

        field = Utf8.toString(b.array(), b.arrayOffset() + b.position(), length);
        b.position(b.position() + length);
        return field;
    }

    @Override
    public Object decode(ByteBuffer b, FastHit hit) {
        Object field = decode(b);
        hit.setField(name, field);
        return field;
    }

    @Override
    public String toString() {
        return "field " + getName() + " type string";
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
        return value.asString("");
    }

}
