// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author havardpe
 */
final class ArrayValue extends Value {

    static final int initial_capacity = 16;
    static final Impl initial_impl = new EmptyImpl();

    private interface Impl {
        void prepareFor(ArrayValue self, Type type);
        Value add(Value value, int used);
        Value add(long value, int used);
        Value add(double value, int used);
        Value get(int index);
    }

    private static final class EmptyImpl implements Impl {
        public void prepareFor(ArrayValue self, Type type) {
            if (type == Type.LONG) {
                self.impl = new LongImpl();
            } else if (type == Type.DOUBLE) {
                self.impl = new DoubleImpl();
            } else {
                self.impl = new GenericImpl(this, 0);
            }
        }
        public Value add(Value value, int used) { return NixValue.invalid(); }
        public Value add(long value, int used) { return NixValue.invalid(); }
        public Value add(double value, int used) { return NixValue.invalid(); }
        public Value get(int index) { return NixValue.invalid(); }
    }

    private static final class LongImpl implements Impl {
        private long[] values = new long[initial_capacity];
        public void prepareFor(ArrayValue self, Type type) {
            if (type != Type.LONG) {
                self.impl = new GenericImpl(this, self.used);
            }
        }
        private static long[] grow(long[] arr) {
            long[] v = new long[arr.length << 1];
            System.arraycopy(arr, 0, v, 0, arr.length);
            return v;
        }
        public Value add(Value value, int used) {
            return NixValue.invalid();
        }
        public Value add(long value, int used) {
            if (used == values.length) {
                values = grow(values);
            }
            values[used] = value;
            return get(used);
        }
        public Value add(double value, int used) { return NixValue.invalid(); }
        public Value get(int index) { return new LongValue(values[index]); }
    }

    private static final class DoubleImpl implements Impl {
        private double[] values = new double[initial_capacity];
        public void prepareFor(ArrayValue self, Type type) {
            if (type != Type.DOUBLE) {
                self.impl = new GenericImpl(this, self.used);
            }
        }
        private static double[] grow(double[] arr) {
            double[] v = new double[arr.length << 1];
            System.arraycopy(arr, 0, v, 0, arr.length);
            return v;
        }
        public Value add(Value value, int used) {
            return NixValue.invalid();
        }
        public Value add(double value, int used) {
            if (used == values.length) {
                values = grow(values);
            }
            values[used] = value;
            return get(used);
        }
        public Value add(long value, int used) { return NixValue.invalid(); }
        public Value get(int index) { return new DoubleValue(values[index]); }
    }

    private static final class GenericImpl implements Impl {
        private Value[] values;
        GenericImpl(Impl src, int len) {
            int capacity = initial_capacity;
            while (capacity < (len + 1)) {
                capacity = capacity << 1;
            }
            values = new Value[capacity];
            for (int i = 0; i < len; i++) {
                values[i] = src.get(i);
            }
        }
        public void prepareFor(ArrayValue self, Type type) {}
        private static Value[] grow(Value[] arr) {
            Value[] v = new Value[arr.length << 1];
            System.arraycopy(arr, 0, v, 0, arr.length);
            return v;
        }
        public Value add(long value, int used) {
            return add(new LongValue(value), used);
        }
        public Value add(double value, int used) {
            return add(new DoubleValue(value), used);
        }
        public Value add(Value value, int used) {
            if (used == values.length) {
                values = grow(values);
            }
            values[used] = value;
            return get(used);
        }
        public Value get(int index) { return values[index]; }
    }

    private Impl impl = initial_impl;
    private int used = 0;
    private final SymbolTable names;

    public ArrayValue(SymbolTable names) { this.names = names; }
    public Type type() { return Type.ARRAY; }
    public int children() { return used; }
    public int entries() { return used; }
    public Value entry(int index) {
        return (index >= 0 && index < used) ? impl.get(index) : NixValue.invalid();
    }

    public void accept(Visitor v) { v.visitArray(this); }

    public void traverse(ArrayTraverser at) {
        for (int i = 0; i < used; i++) {
            at.entry(i, impl.get(i));
        }
    }

    @Override
    public Cursor addLong(long value) {
        impl.prepareFor(this, Type.LONG);
        return impl.add(value, used++);
    }

    @Override
    public Cursor addDouble(double value) {
        impl.prepareFor(this, Type.DOUBLE);
        return impl.add(value, used++);
    }

    protected Value addLeaf(Value value) {
        impl.prepareFor(this, value.type());
        return impl.add(value, used++);
    }

    public Value addArray() { return addLeaf(new ArrayValue(names)); }
    public Value addObject() { return addLeaf(new ObjectValue(names)); }
}
