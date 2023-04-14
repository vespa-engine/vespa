// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import static org.junit.Assert.assertEquals;

class MockVisitor implements Visitor {
    enum Called { NONE, INVALID, NIX, BOOL, LONG, DOUBLE, STRING, UTF8, DATA, ARRAY, OBJECT }
    Called called = Called.NONE;
    boolean boolValue;
    long longValue;
    double doubleValue;
    String string;
    byte[] bytes;
    Inspector stuff;
    String ctx;

    MockVisitor(String context) {
        ctx = context;
    }
    MockVisitor() { this(""); }
    @Override public void visitInvalid() {
        assertEquals(ctx, Called.NONE, called);
        called = Called.INVALID;
    }
    @Override public void visitNix() {
        assertEquals(ctx, Called.NONE, called);
        called = Called.NIX;
    }
    @Override public void visitBool(boolean bit) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.BOOL;
        boolValue = bit;
    }
    @Override public void visitLong(long l) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.LONG;
        longValue = l;
    }
    @Override public void visitDouble(double d) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.DOUBLE;
        doubleValue = d;
    }
    @Override public void visitString(String str) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.STRING;
        string = str;
    }
    @Override public void visitString(byte[] utf8) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.UTF8;
        bytes = utf8;
    }
    @Override public void visitData(byte[] data) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.DATA;
        bytes = data;
    }
    @Override public void visitArray(Inspector arr) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.ARRAY;
        stuff = arr;
    }
    @Override public void visitObject(Inspector obj) {
        assertEquals(ctx, Called.NONE, called);
        called = Called.OBJECT;
        stuff = obj;
    }
}
