// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.nio.ByteBuffer;

import com.yahoo.io.SlowInflate;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.text.Utf8;
import com.yahoo.data.access.*;
import com.yahoo.data.access.simple.Value;

/**
 * A hit field containing JSON structured data
 *
 * @author Steinar Knutsen
 */
public class JSONField extends DocsumField implements VariableLengthField {

    public JSONField(String name) {
        super(name);
    }

    @Override
    public Object decode(ByteBuffer b) {
        long dataLen = 0;
        long len = ((long) b.getInt()) & 0xffffffffL;
        boolean compressed;
        JSONString field;

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

        field = new JSONString(Utf8.toString(tmp));
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
        return "field " + getName() + " type JSONString";
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

    private static class CompatibilityConverter {
        Value.ArrayValue target = new Value.ArrayValue();

        Inspector stringify(Inspector value) {
            if (value.type() == Type.STRING) return value;
            if (value.type() == Type.LONG) {
                String str = String.valueOf(value.asLong());
                return new Value.StringValue(str);
            }
            if (value.type() == Type.DOUBLE) {
                String str = String.valueOf(value.asDouble());
                return new Value.StringValue(str);
            }
            String str = value.toString();
            return new Value.StringValue(str);
        }
    }

    private static class ArrConv extends CompatibilityConverter implements ArrayTraverser {
        @Override
        public void entry(int idx, Inspector value) {
            target.add(stringify(value));
        }
    }

    private static class WsConv1 extends CompatibilityConverter
        implements ArrayTraverser
    {
        @Override
        public void entry(int idx, Inspector value) {
            Value.ArrayValue obj = new Value.ArrayValue();
            obj.add(stringify(value.entry(0)));
            obj.add(value.entry(1));
            target.add(obj);
        }
    }

    private static class WsConv2 extends CompatibilityConverter
        implements ArrayTraverser
    {
        @Override
        public void entry(int idx, Inspector value) {
            Value.ArrayValue obj = new Value.ArrayValue();
            obj.add(stringify(value.field("item")));
            obj.add(value.field("weight"));
            target.add(obj);
        }
    }

    static Inspector convertTop(Inspector value) {
        if (value.type() == Type.ARRAY && value.entryCount() > 0) {
            Inspector first = value.entry(0);
            if (first.type() == Type.ARRAY && first.entryCount() == 2) {
                // old style weighted set
                WsConv1 conv = new WsConv1();
                value.traverse(conv);
                return conv.target;
            }
            if (first.type() == Type.OBJECT &&
                first.fieldCount() == 2 &&
                first.field("item").valid() &&
                first.field("weight").valid())
            {
                // new style weighted set
                WsConv2 conv = new WsConv2();
                value.traverse(conv);
                return conv.target;
            }
            if (first.type() == Type.LONG) {
                ArrConv conv = new ArrConv();
                value.traverse(conv);
                return conv.target;
            }
            if (first.type() == Type.DOUBLE) {
                ArrConv conv = new ArrConv();
                value.traverse(conv);
                return conv.target;
            }
        }
        return value;
    }

    public Object convert(Inspector value) {
        if (value.valid()) {
            return new JSONString(convertTop(value));
        } else {
            return new JSONString("");
        }
    }
}
