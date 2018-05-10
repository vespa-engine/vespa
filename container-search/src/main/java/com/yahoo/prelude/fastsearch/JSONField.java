// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class JSONField extends DocsumField {

    public JSONField(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type JSONString";
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

    @Override
    public Object convert(Inspector value) {
        if (value.valid()) {
            return new JSONString(convertTop(value));
        } else {
            return new JSONString("");
        }
    }

}
