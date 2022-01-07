// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;


import static com.yahoo.slime.BinaryFormat.decode_double;
import static com.yahoo.slime.BinaryFormat.decode_meta;
import static com.yahoo.slime.BinaryFormat.decode_type;
import static com.yahoo.slime.BinaryFormat.decode_zigzag;

final class BinaryDecoder {

    BufferedInput in;

    private final SlimeInserter slimeInserter = new SlimeInserter(null);
    private final ArrayInserter arrayInserter = new ArrayInserter(null);
    private final ObjectSymbolInserter objectInserter = new ObjectSymbolInserter(null, 0);

    public BinaryDecoder() {}

    public Slime decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }
    public Slime decode(byte[] bytes, int offset, int length) {
        Slime slime = new Slime();
        in = new BufferedInput(bytes, offset, length);
        decodeSymbolTable(slime);
        decodeValue(slimeInserter.adjust(slime));
        if (in.failed()) {
            slime.wrap("partial_result");
            slime.get().setData("offending_input", in.getOffending());
            slime.get().setString("error_message", in.getErrorMessage());
        }
        return slime;
    }

    long read_cmpr_long() {
        long next = in.getByte();
        long value = (next & 0x7f);
        int shift = 7;
        while ((next & 0x80) != 0) {
            next = in.getByte();
            value |= ((next & 0x7f) << shift);
            shift += 7;
        }
        return value;
    }

    long read_size(int meta) {
        return (meta == 0) ? read_cmpr_long() : (meta - 1);
    }

    long read_bytes_le(int bytes) {
        long value = 0;
        int shift = 0;
        for (int i = 0; i < bytes; ++i) {
            long b = in.getByte();
            value |= (b & 0xff) << shift;
            shift += 8;
        }
        return value;
    }

    long read_bytes_be(int bytes) {
        long value = 0;
        int shift = 56;
        for (int i = 0; i < bytes; ++i) {
            long b = in.getByte();
            value |= (b & 0xff) << shift;
            shift -= 8;
        }
        return value;
    }

    Cursor decodeNIX(Inserter inserter) {
        return inserter.insertNIX();
    }

    Cursor decodeBOOL(Inserter inserter, int meta) {
        return inserter.insertBOOL(meta != 0);
    }

    Cursor decodeLONG(Inserter inserter, int meta) {
        long encoded = read_bytes_le(meta);
        return inserter.insertLONG(decode_zigzag(encoded));
    }

    Cursor decodeDOUBLE(Inserter inserter, int meta) {
        long encoded = read_bytes_be(meta);
        return inserter.insertDOUBLE(decode_double(encoded));
    }

    Cursor decodeSTRING(Inserter inserter, int meta) {
        long size = read_size(meta);
        int sz = (int)size; // XXX
        byte[] image = in.getBytes(sz);
        return inserter.insertSTRING(image);
    }

    Cursor decodeDATA(Inserter inserter, int meta) {
        long size = read_size(meta);
        int sz = (int)size; // XXX
        byte[] image = in.getBytes(sz);
        return inserter.insertDATA(image);
    }

    Cursor decodeARRAY(Inserter inserter, int meta) {
        Cursor cursor = inserter.insertARRAY();
        long size = read_size(meta);
        for (int i = 0; i < size; ++i) {
            decodeValue(arrayInserter.adjust(cursor));
        }
        return cursor;
    }

    Cursor decodeOBJECT(Inserter inserter, int meta) {
        Cursor cursor = inserter.insertOBJECT();
        long size = read_size(meta);
        for (int i = 0; i < size; ++i) {
            long l = read_cmpr_long();
            int symbol = (int)l; // check for overflow?
            decodeValue(objectInserter.adjust(cursor, symbol));
        }
        return cursor;
    }

    Cursor decodeValue(Inserter inserter, Type type, int meta) {
        switch (type) {
        case NIX:     return decodeNIX(inserter);
        case BOOL:    return decodeBOOL(inserter, meta);
        case LONG:    return decodeLONG(inserter, meta);
        case DOUBLE:  return decodeDOUBLE(inserter, meta);
        case STRING:  return decodeSTRING(inserter, meta);
        case DATA:    return decodeDATA(inserter, meta);
        case ARRAY:   return decodeARRAY(inserter, meta);
        case OBJECT:  return decodeOBJECT(inserter, meta);
        }
        assert false : "should not be reached";
        return null;
    }

    void decodeValue(Inserter inserter) {
        byte b = in.getByte();
        Cursor cursor = decodeValue(inserter, decode_type(b), decode_meta(b));
        if (!cursor.valid()) {
            in.fail("failed to decode value");
        }
    }

    void decodeSymbolTable(Slime slime) {
        long numSymbols = read_cmpr_long();
        final byte [] backing = in.getBacking();
        for (int i = 0; i < numSymbols; ++i) {
            long size = read_cmpr_long();
            int sz = (int)size; // XXX
            int offset = in.getPosition();
            in.skip(sz);
            int symbol = slime.insert(Utf8Codec.decode(backing, offset, sz));
            if (symbol != i) {
                in.fail("duplicate symbols in symbol table");
                return;
            }
        }
    }

}
