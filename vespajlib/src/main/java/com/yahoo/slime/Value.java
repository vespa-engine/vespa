// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/**
 * Common implementation for all value types.
 * All default behavior is here, so specific types only
 * need override their actually useful parts
 *
 * @author havardpe
 */
abstract class Value implements Cursor {

    static final String emptyString = "";
    static final byte[] emptyData = new byte[0];

    public final boolean valid() { return this != NixValue.invalid(); }

    public final void ifValid(Consumer<Inspector> consumer) {
        if (valid()) consumer.accept(this);
    }

    public int children() { return 0; }
    public int entries() { return 0; }
    public int fields() { return 0; }

    public boolean asBool() { return false; }
    public long asLong() { return 0; }
    public double asDouble() { return 0.0; }
    public String asString() { return emptyString; }
    public byte[] asUtf8() { return emptyData; }
    public byte[] asData() { return emptyData; }

    public void traverse(ArrayTraverser at) {}
    public void traverse(ObjectSymbolTraverser ot) {}
    public void traverse(ObjectTraverser ot) {}

    public Value entry(int idx) { return NixValue.invalid(); }
    public Value field(String name) { return NixValue.invalid(); }
    public Value field(int sym) { return NixValue.invalid(); }

    protected Cursor addLeaf(Value value) { return NixValue.invalid(); }
    public Cursor addArray() { return NixValue.invalid(); }
    public Cursor addObject() { return NixValue.invalid(); }

    public final Cursor addNix() { return addLeaf(NixValue.instance()); }
    public final Cursor addBool(boolean bit) { return addLeaf(BoolValue.instance(bit)); }
    public Cursor addLong(long l) { return NixValue.invalid(); }
    public Cursor addDouble(double d) { return NixValue.invalid(); }
    public final Cursor addString(String str) { return addLeaf(StringValue.create(str)); }
    public final Cursor addString(byte[] utf8) { return addLeaf(Utf8Value.create(utf8)); }
    public final Cursor addData(byte[] data) { return addLeaf(DataValue.create(data)); }

    protected Cursor setLeaf(int sym, Value value) { return NixValue.invalid(); }
    public Cursor setArray(int sym) { return NixValue.invalid(); }
    public Cursor setObject(int sym) { return NixValue.invalid(); }

    public final Cursor setNix(int sym) { return setLeaf(sym, NixValue.instance()); }
    public final Cursor setBool(int sym, boolean bit) { return setLeaf(sym, BoolValue.instance(bit)); }
    public final Cursor setLong(int sym, long l) { return setLeaf(sym, new LongValue(l)); }
    public final Cursor setDouble(int sym, double d) { return setLeaf(sym, new DoubleValue(d)); }
    public final Cursor setString(int sym, String str) { return setLeaf(sym, StringValue.create(str)); }
    public final Cursor setString(int sym, byte[] utf8) { return setLeaf(sym, Utf8Value.create(utf8)); }
    public final Cursor setData(int sym, byte[] data) { return setLeaf(sym, DataValue.create(data)); }

    protected Cursor setLeaf(String name, Value value) { return NixValue.invalid(); }
    public Cursor setArray(String name) { return NixValue.invalid(); }
    public Cursor setObject(String name) { return NixValue.invalid(); }

    public final Cursor setNix(String name) { return setLeaf(name, NixValue.instance()); }
    public final Cursor setBool(String name, boolean bit) { return setLeaf(name, BoolValue.instance(bit)); }
    public final Cursor setLong(String name, long l) { return setLeaf(name, new LongValue(l)); }
    public final Cursor setDouble(String name, double d) { return setLeaf(name, new DoubleValue(d)); }
    public final Cursor setString(String name, String str) { return setLeaf(name, StringValue.create(str)); }
    public final Cursor setString(String name, byte[] utf8) { return setLeaf(name, Utf8Value.create(utf8)); }
    public final Cursor setData(String name, byte[] data) { return setLeaf(name, DataValue.create(data)); }

    public final String toString() {
        try {
            // should produce non-compact json, but we need compact
            // json for slime summaries until we have a more generic
            // json rendering pipeline in place.
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            new JsonFormat(true).encode(a, this);
            byte[] utf8 = a.toByteArray();
            return Utf8Codec.decode(utf8, 0, utf8.length);
        } catch (Exception e) {
            return "null";
        }
    }
}
