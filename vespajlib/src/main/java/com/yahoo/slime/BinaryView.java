// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.util.function.Consumer;
import static com.yahoo.slime.BinaryFormat.decode_double;
import static com.yahoo.slime.BinaryFormat.decode_meta;
import static com.yahoo.slime.BinaryFormat.decode_type;
import static com.yahoo.slime.BinaryFormat.decode_zigzag;

/**
 * A read-only view of a Slime value that is stored in binary format.
 **/
public final class BinaryView implements Inspector {

    private final byte[] data;
    private final SymbolTable names;
    private final DecodeIndex index;
    private final int self;

    private BinaryView(byte[] data, SymbolTable names, DecodeIndex index, int self) {
        this.data = data;
        this.names = names;
        this.index = index;
        this.self = self;
    }
    private int peek_cmpr_int(int idx) {
        long next = data[idx++];
        long value = (next & 0x7f);
        int shift = 7;
        while ((next & 0x80) != 0) {
            next = data[idx++];
            value |= ((next & 0x7f) << shift);
            shift += 7;
        }
        return (int)value;
    }
    private int skip_cmpr_int(int idx) {
        while ((data[idx++] & 0x80) != 0);
        return idx;
    }
    private int extract_children(int idx) {
        int bytes = decode_meta(data[idx++]);
        return (bytes == 0)
                ? peek_cmpr_int(idx)
                : (bytes - 1);
    }
    private long extract_long(int idx) {
        int bytes = decode_meta(data[idx++]);
        long value = 0;
        int shift = 0;
        for (int i = 0; i < bytes; ++i) {
            long b = data[idx++];
            value |= (b & 0xff) << shift;
            shift += 8;
        }
        return decode_zigzag(value);
    }
    private double extract_double(int idx) {
        int bytes = decode_meta(data[idx++]);
        long value = 0;
        int shift = 56;
        for (int i = 0; i < bytes; ++i) {
            long b = data[idx++];
            value |= (b & 0xff) << shift;
            shift -= 8;
        }
        return decode_double(value);
    }
    private String extract_string(int idx) {
        int bytes = decode_meta(data[idx++]);
        if (bytes == 0) {
            bytes = peek_cmpr_int(idx);
            idx = skip_cmpr_int(idx);
        } else {
            --bytes;
        }
        return Utf8Codec.decode(data, idx, bytes);
    }
    private byte[] extract_bytes(int idx) {
        int bytes = decode_meta(data[idx++]);
        if (bytes == 0) {
            bytes = peek_cmpr_int(idx);
            idx = skip_cmpr_int(idx);
        } else {
            --bytes;
        }
        byte[] ret = new byte[bytes];
        for (int i = 0; i < bytes; ++i) {
            ret[i] = data[idx++];
        }
        return ret;
    }
    private Inspector find_field(int pos, int len, int sym) {
        for (int i = 0; i < len; ++i) {
            int idx = index.getByteOffset(pos + i);
            if (peek_cmpr_int(idx - (index.getExtBits(pos + i) + 1)) == sym) {
                return new BinaryView(data, names, index, pos + i);
            }
        }
        return NixValue.invalid();
    }

    @Override public boolean valid() { return true; }
    @Override public void ifValid(Consumer<Inspector> consumer) { consumer.accept(this); }
    @Override public Type type() { return decode_type(data[index.getByteOffset(self)]); }
    @Override public int children() {
        return switch (type()) {
            case OBJECT, ARRAY -> extract_children(index.getByteOffset(self));
            default -> 0;
        };
    }
    @Override public int entries() {
        return switch (type()) {
            case ARRAY -> extract_children(index.getByteOffset(self));
            default -> 0;
        };
    }
    @Override public int fields() {
        return switch (type()) {
            case OBJECT -> extract_children(index.getByteOffset(self));
            default -> 0;
        };
    }
    @Override public boolean asBool() {
        return switch (type()) {
            case BOOL -> (decode_meta(data[index.getByteOffset(self)]) != 0);
            default -> false;
        };
    }
    @Override public long asLong() {
        return switch (type()) {
            case LONG -> extract_long(index.getByteOffset(self));
            case DOUBLE -> (long)extract_double(index.getByteOffset(self));
            default -> 0;
        };
    }
    @Override public double asDouble() {
        return switch (type()) {
            case LONG -> extract_long(index.getByteOffset(self));
            case DOUBLE -> extract_double(index.getByteOffset(self));
            default -> 0.0;
        };
    }
    @Override public String asString() {
        return switch (type()) {
            case STRING -> extract_string(index.getByteOffset(self));
            default -> Value.emptyString;
        };
    }
    @Override public byte[] asUtf8() {
        return switch (type()) {
            case STRING -> extract_bytes(index.getByteOffset(self));
            default -> Value.emptyData;
        };
    }
    @Override public byte[] asData() {
        return switch (type()) {
            case DATA -> extract_bytes(index.getByteOffset(self));
            default -> Value.emptyData;
        };
    }
    @Override public void accept(Visitor v) {
        switch (type()) {
            case NIX:    v.visitNix(); break;
            case BOOL:   v.visitBool(decode_meta(data[index.getByteOffset(self)]) != 0); break;
            case LONG:   v.visitLong(extract_long(index.getByteOffset(self))); break;
            case DOUBLE: v.visitDouble(extract_double(index.getByteOffset(self))); break;
            case STRING: v.visitString(extract_bytes(index.getByteOffset(self))); break;
            case DATA:   v.visitData(extract_bytes(index.getByteOffset(self))); break;
            case ARRAY:  v.visitArray(this); break;
            case OBJECT: v.visitObject(this); break;
            default: throw new RuntimeException("should not be reached");
        }
    }
    @Override public void traverse(ArrayTraverser at) {
        int pos = index.getFirstChild(self);
        int len = entries();
        for (int i = 0; i < len; ++i) {
            at.entry(i, new BinaryView(data, names, index, pos + i));
        }
    }
    @Override public void traverse(ObjectSymbolTraverser ot) {
        int pos = index.getFirstChild(self);
        int len = fields();
        for (int i = 0; i < len; ++i) {
            int sym = peek_cmpr_int(index.getByteOffset(pos + i) - (index.getExtBits(pos + i) + 1));
            ot.field(sym, new BinaryView(data, names, index, pos + i));
        }
    }
    @Override public void traverse(ObjectTraverser ot) {
        int pos = index.getFirstChild(self);
        int len = fields();
        for (int i = 0; i < len; ++i) {
            int sym = peek_cmpr_int(index.getByteOffset(pos + i) - (index.getExtBits(pos + i) + 1));
            ot.field(names.inspect(sym), new BinaryView(data, names, index, pos + i));
        }
    }
    @Override public Inspector entry(int idx) {
        int limit = entries();
        if (idx >= 0 && idx < limit) {
            return new BinaryView(data, names, index, index.getFirstChild(self) + idx);
        }
        return NixValue.invalid();
    }
    @Override public Inspector field(int sym) {
        int limit = fields();
        if (limit > 0 && sym != SymbolTable.INVALID) {
            return find_field(index.getFirstChild(self), limit, sym);
        }
        return NixValue.invalid();
    }
    @Override public Inspector field(String name) {
        int limit = fields();
        if (limit > 0) {
            int sym = names.lookup(name);
            if (sym != SymbolTable.INVALID) {
                return find_field(index.getFirstChild(self), limit, sym);
            }
        }
        return NixValue.invalid();
    }

    private static void buildIndex(BufferedInput input, DecodeIndex index, int self, int extBits) {
        int pos = input.getPosition();
        byte tag = input.getByte();
        Type type = decode_type(tag);
        int meta = decode_meta(tag);
        switch (type) {
            case NIX:
            case BOOL:
                index.set(self, pos, 0, extBits);
                break;
            case LONG:
            case DOUBLE:
                input.skip(meta);
                index.set(self, pos, 0, extBits);
                break;
            case STRING:
            case DATA: {
                int size = input.read_size(meta);
                input.skip(size);
                index.set(self, pos, 0, extBits);
                break; }
            case ARRAY: {
                int size = input.read_size(meta);
                if (size > input.getBacking().length - index.size()) {
                    input.fail("decode index too big");
                    return;
                }
                int firstChild = index.reserve(size);
                index.set(self, pos, firstChild, extBits);
                for (int i = 0; i < size; ++i) {
                    buildIndex(input, index, firstChild + i, 0);
                }
                break; }
            case OBJECT: {
                int size = input.read_size(meta);
                if (size > input.getBacking().length - index.size()) {
                    input.fail("decode index too big");
                    return;
                }
                int firstChild = index.reserve(size);
                index.set(self, pos, firstChild, extBits);
                for (int i = 0; i < size; ++i) {
                    int childExtBits = input.skip_cmpr_int();
                    if (childExtBits > 3) {
                        input.fail("symbol id too big");
                        return;
                    }
                    buildIndex(input, index, firstChild + i, childExtBits);
                }
                break; }
            default: throw new RuntimeException("should not be reached");
        }
    }

    static Inspector inspectImpl(BufferedInput input) {
        var names = new SymbolTable();
        var index = new DecodeIndex();
        BinaryDecoder.decodeSymbolTable(input, names);
        buildIndex(input, index, index.reserve(1), 0);
        if (input.failed()) {
            return NixValue.invalid();
        }
        return new BinaryView(input.getBacking(), names, index, 0);
    }

    public static Inspector inspect(byte[] data) {
        return inspectImpl(new BufferedInput(data));
    }

    static int peek_cmpr_int_for_testing(byte[] data, int idx) {
        return new BinaryView(data, null, null, -1).peek_cmpr_int(idx);
    }
    static int skip_cmpr_int_for_testing(byte[] data, int idx) {
        return new BinaryView(data, null, null, -1).skip_cmpr_int(idx);
    }
    static int extract_children_for_testing(byte[] data, int idx) {
        return new BinaryView(data, null, null, -1).extract_children(idx);
    }
    static long extract_long_for_testing(byte[] data, int idx) {
        return new BinaryView(data, null, null, -1).extract_long(idx);
    }
    static double extract_double_for_testing(byte[] data, int idx) {
        return new BinaryView(data, null, null, -1).extract_double(idx);
    }
}
