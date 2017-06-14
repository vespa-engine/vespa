// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;


final class ArrayValue extends Value {

    private int capacity = 16;
    private int used = 0;
    private Value[] values = new Value[capacity];
    private final SymbolTable names;

    public ArrayValue(SymbolTable names) { this.names = names; }
    public final Type type() { return Type.ARRAY; }
    public final int children() { return used; }
    public final int entries() { return used; }
    public final Value entry(int index) {
        return (index < used) ? values[index] : NixValue.invalid();
    }

    public final void accept(Visitor v) { v.visitArray(this); }

    public final void traverse(ArrayTraverser at) {
        for (int i = 0; i < used; i++) {
            at.entry(i, values[i]);
        }
    }

    private void grow() {
        Value[] v = values;
        capacity = (capacity << 1);
        values = new Value[capacity];
        System.arraycopy(v, 0, values, 0, used);
    }

    protected final Value addLeaf(Value value) {
        if (used == capacity) {
            grow();
        }
        values[used++] = value;
        return value;
    }

    public final Value addArray() { return addLeaf(new ArrayValue(names)); }
    public final Value addObject() { return addLeaf(new ObjectValue(names)); }
}
