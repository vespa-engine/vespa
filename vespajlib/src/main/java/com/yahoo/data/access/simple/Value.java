// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.simple;

import com.yahoo.data.access.*;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;

public class Value implements Inspector {

    private static final Value empty = new EmptyValue();
    private static final Value invalid = new Value();
    private static final byte[] empty_array = new byte[0];
    public static Inspector empty() { return empty; }
    public static Inspector invalid() { return invalid; }
    public Inspector inspect() { return this; }
    public boolean valid() { return false; }
    public Type type() { return Type.EMPTY; }
    public int entryCount() { return 0; }
    public int fieldCount() { return 0; }
    public boolean asBool() { throw new IllegalStateException("invalid data access!"); }
    public long asLong() { throw new IllegalStateException("invalid data access!"); }
    public double asDouble() { throw new IllegalStateException("invalid data access!"); }
    public java.lang.String asString() { throw new IllegalStateException("invalid data access!"); }
    public byte[] asUtf8() { throw new IllegalStateException("invalid data access!"); }
    public byte[] asData() { throw new IllegalStateException("invalid data access!"); }
    public boolean asBool(boolean defaultValue) { return defaultValue; }
    public long asLong(long defaultValue) { return defaultValue; }
    public double asDouble(double defaultValue) { return defaultValue; }
    public java.lang.String asString(java.lang.String defaultValue) { return defaultValue; }
    public byte[] asUtf8(byte[] defaultValue) { return defaultValue; }
    public byte[] asData(byte[] defaultValue) { return defaultValue; }
    public void traverse(ArrayTraverser at) {}
    public void traverse(ObjectTraverser ot) {}
    public Inspector entry(int idx) { return invalid; }
    public Inspector field(java.lang.String name) { return invalid; }
    public Iterable<Inspector> entries() { return Collections.emptyList(); }
    public Iterable<Map.Entry<java.lang.String,Inspector>> fields() { return Collections.emptyList(); }
    public StringBuilder writeJson(StringBuilder target) {
        return JsonRender.render(this, target, true);
    }
    public String toJson() { return writeJson(new StringBuilder()).toString(); }
    public String toString() { return toJson(); }
    static public class EmptyValue extends Value {
        public boolean valid() { return true; }
        public boolean asBool() { return false; }
        public long asLong() { return 0L; }
        public double asDouble() { return 0.0; }
        public java.lang.String asString() { return ""; }
        public byte[] asUtf8() { return empty_array; }
        public byte[] asData() { return empty_array; }
    }
    static public class BoolValue extends Value {
        private boolean value;
        public BoolValue(boolean v) { value = v; }
        public boolean valid() { return true; }
        public Type type() { return Type.BOOL; }
        public boolean asBool() { return value; }
        public boolean asBool(boolean x) { return value; }
    }
    static public class LongValue extends Value {
        private long value;
        public LongValue(long v) { value = v; }
        public boolean valid() { return true; }
        public Type type() { return Type.LONG; }
        public long asLong() { return value; }
        public double asDouble() { return (double)value; }
        public long asLong(long x) { return value; }
        public double asDouble(double x) { return (double)value; }
    }
    static public class DoubleValue extends Value {
        private double value;
        public DoubleValue(double v) { value = v; }
        public boolean valid() { return true; }
        public Type type() { return Type.DOUBLE; }
        public double asDouble() { return value; }
        public long asLong() { return (long)value; }
        public double asDouble(double x) { return value; }
        public long asLong(long x) { return (long)value; }
    }
    static public class StringValue extends Value {
        private java.lang.String string_value = null;
        private byte[] utf8_value = null;
        private void handle_null() {
            if (string_value == null && utf8_value == null) {
                string_value = "";
                utf8_value = empty_array;
            }
        }
        public StringValue(java.lang.String v) {
            string_value = v;
            handle_null();
        }
        public StringValue(byte[] v) {
            utf8_value = v;
            handle_null();
        }
        public boolean valid() { return true; }
        public Type type() { return Type.STRING; }
        public java.lang.String asString() {
            if (string_value == null) {
                string_value = new java.lang.String(utf8_value, StandardCharsets.UTF_8);
            }
            return string_value;
        }
        public java.lang.String asString(java.lang.String x) { return asString(); }
        public byte[] asUtf8() {
            if (utf8_value == null) {
                utf8_value = string_value.getBytes(StandardCharsets.UTF_8);
            }
            return utf8_value;
        }
        public byte[] asUtf8(byte[] x) { return asUtf8(); }
    }
    static public class DataValue extends Value {
        private byte[] value;
        public DataValue(byte[] v) {
            value = v;
            if (v == null) {
                value = empty_array;
            }
        }
        public boolean valid() { return true; }
        public Type type() { return Type.DATA; }
        public byte[] asData() { return value; }
        public byte[] asData(byte[] x) { return value; }
    }
    static public class ArrayValue extends Value {
        private List<Inspector> values = new ArrayList<>();
        public boolean valid() { return true; }
        public Type type() { return Type.ARRAY; }
        public int entryCount() { return values.size(); }
        public Inspector entry(int idx) {
            if (idx < 0 || idx >= values.size()) {
                return invalid;
            }
            return values.get(idx);
        }
        public void traverse(ArrayTraverser at) {
            int idx = 0;
            for (Inspector i: values) {
                at.entry(idx++, i);
            }
        }
        public Iterable<Inspector> entries() {
            return Collections.unmodifiableList(values);
        }
        public ArrayValue add(Inspector v) {
            if (v == null || !v.valid()) {
                throw new IllegalArgumentException("tried to add an invalid value to an array");
            }
            values.add(v);
            return this;
        }
        public ArrayValue add(java.lang.String value) {
            return add(new Value.StringValue(value));
        }
        public ArrayValue add(long value) {
            return add(new Value.LongValue(value));
        }
        public ArrayValue add(int value) {
            return add(new Value.LongValue(value));
        }
        public ArrayValue add(double value) {
            return add(new Value.DoubleValue(value));
        }
    }
    static public class ObjectValue extends Value {
        private Map<java.lang.String,Inspector> values = new LinkedHashMap<>();
        public boolean valid() { return true; }
        public Type type() { return Type.OBJECT; }
        public int fieldCount() { return values.size(); }
        public Inspector field(java.lang.String name) {
            Inspector v = values.get(name);
            if (v == null) {
                return invalid;
            }
            return v;
        }
        public void traverse(ObjectTraverser ot) {
            for (Map.Entry<java.lang.String,Inspector> i: values.entrySet()) {
                ot.field(i.getKey(), i.getValue());
            }
        }
        public Iterable<Map.Entry<java.lang.String,Inspector>> fields() {
            return Collections.<java.lang.String,Inspector>unmodifiableMap(values).entrySet();
        }
        public ObjectValue put(java.lang.String name, Inspector v) {
            if (name == null) {
                throw new IllegalArgumentException("field name was <null>");
            }
            if (v == null || !v.valid()) {
                throw new IllegalArgumentException("tried to put an invalid value into an object");
            }
            values.put(name, v);
            return this;
        }
        public ObjectValue put(java.lang.String name, java.lang.String value) {
            return put(name, new Value.StringValue(value));
        }
        public ObjectValue put(java.lang.String name, long value) {
            return put(name, new Value.LongValue(value));
        }
        public ObjectValue put(java.lang.String name, int value) {
            return put(name, new Value.LongValue(value));
        }
        public ObjectValue put(java.lang.String name, double value) {
            return put(name, new Value.DoubleValue(value));
        }
    }
}
