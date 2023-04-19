// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import static com.yahoo.slime.BinaryFormat.encode_double;
import static com.yahoo.slime.BinaryFormat.encode_type_and_meta;
import static com.yahoo.slime.BinaryFormat.encode_zigzag;

final class BinaryEncoder implements ArrayTraverser, ObjectSymbolTraverser {

    private final BufferedOutput out;

    BinaryEncoder() {
        this(new BufferedOutput());
    }
    BinaryEncoder(BufferedOutput output) {
        out = output;
    }

    BufferedOutput encode(Slime slime) {
        out.reset();
        encodeSymbolTable(slime);
        encodeValue(slime.get());
        return out;
    }


    void encode_cmpr_int(int value) {
        byte next = (byte)(value & 0x7f);
        value >>>= 7; // unsigned shift
        while (value != 0) {
            next |= (byte)0x80;
            out.put(next);
            next = (byte)(value & 0x7f);
            value >>>= 7;
        }
        out.put(next);
    }

    void write_type_and_size(int type, int size) {
        if (size <= 30) {
            out.put(encode_type_and_meta(type, size + 1));
        } else {
            out.put(encode_type_and_meta(type, 0));
            encode_cmpr_int(size);
        }
    }

    void write_type_and_bytes_le(int type, long bits) {
        int pos = out.position();
        byte val = 0;
        out.put(val);
        while (bits != 0) {
            val = (byte)(bits & 0xff);
            bits >>>= 8;
            out.put(val);
        }
        val = encode_type_and_meta(type, out.position() - pos - 1);
        out.absolutePut(pos, val);
    }

    void write_type_and_bytes_be(int type, long bits) {
        int pos = out.position();
        byte val = 0;
        out.put(val);
        while (bits != 0) {
            val = (byte)(bits >> 56);
            bits <<= 8;
            out.put(val);
        }
        val = encode_type_and_meta(type, out.position() - pos - 1);
        out.absolutePut(pos, val);
    }

    void encodeNIX() {
        out.put(Type.NIX.ID);
    }

    void encodeBOOL(boolean value) {
        out.put(encode_type_and_meta(Type.BOOL.ID, value ? 1 : 0));
    }

    void encodeLONG(long value) {
        write_type_and_bytes_le(Type.LONG.ID, encode_zigzag(value));
    }

    void encodeDOUBLE(double value) {
        write_type_and_bytes_be(Type.DOUBLE.ID, encode_double(value));
    }

    void encodeSTRING(byte[] value) {
        write_type_and_size(Type.STRING.ID, value.length);
        out.put(value);
    }

    void encodeDATA(byte[] value) {
        write_type_and_size(Type.DATA.ID, value.length);
        out.put(value);
    }

    void encodeARRAY(Inspector inspector) {
        write_type_and_size(Type.ARRAY.ID, inspector.children());
        ArrayTraverser at = this;
        inspector.traverse(at);
    }

    void encodeOBJECT(Inspector inspector) {
        write_type_and_size(Type.OBJECT.ID, inspector.children());
        ObjectSymbolTraverser ot = this;
        inspector.traverse(ot);
    }

    void encodeValue(Inspector inspector) {
        switch(inspector.type()) {
        case NIX:    encodeNIX();                        return;
        case BOOL:   encodeBOOL(inspector.asBool());     return;
        case LONG:   encodeLONG(inspector.asLong());     return;
        case DOUBLE: encodeDOUBLE(inspector.asDouble()); return;
        case STRING: encodeSTRING(inspector.asUtf8());   return;
        case DATA:   encodeDATA(inspector.asData());     return;
        case ARRAY:  encodeARRAY(inspector);             return;
        case OBJECT: encodeOBJECT(inspector);            return;
        }
        assert false : "Should not be reached";
    }

    void encodeSymbolTable(Slime slime) {
        int numSymbols = slime.symbols();
        encode_cmpr_int(numSymbols);
        for (int i = 0 ; i < numSymbols; ++i) {
            String name = slime.inspect(i);
            byte[] bytes = Utf8Codec.encode(name);
            encode_cmpr_int(bytes.length);
            out.put(bytes);
        }
    }

    public void entry(int idx, Inspector inspector) {
        encodeValue(inspector);
    }

    public void field(int symbol, Inspector inspector) {
        encode_cmpr_int(symbol);
        encodeValue(inspector);
    }

}
