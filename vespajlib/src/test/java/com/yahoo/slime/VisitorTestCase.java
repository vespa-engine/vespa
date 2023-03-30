// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

public class VisitorTestCase {

    @Test
    public void testVisitInvalid() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().get().field("invalid");
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.INVALID, visitor.called);
    }

    @Test
    public void testVisitNix() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().get();
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.NIX, visitor.called);
    }

    @Test
    public void testVisitBool() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setBool(true);
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.BOOL, visitor.called);
        assertEquals(true, visitor.boolValue);
    }

    @Test
    public void testVisitLong() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setLong(123);
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.LONG, visitor.called);
        assertEquals(123, visitor.longValue);
    }

    @Test
    public void testVisitDouble() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setDouble(123.0);
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.DOUBLE, visitor.called);
        assertEquals(123.0, visitor.doubleValue, 0.0);
    }

    @Test
    public void testVisitStringUtf16() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setString("abc");
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.STRING, visitor.called);
        assertEquals("abc", visitor.string);
    }

    @Test
    public void testVisitStringUtf8() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setString(new byte[] {65,66,67});
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.UTF8, visitor.called);
        assertArrayEquals(new byte[] {65,66,67}, visitor.bytes);
    }

    @Test
    public void testVisitData() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setData(new byte[] {1,2,3});
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.DATA, visitor.called);
        assertArrayEquals(new byte[] {1,2,3}, visitor.bytes);
    }

    @Test
    public void testVisitArray() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setArray();
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.ARRAY, visitor.called);
        assertSame(inspector, visitor.stuff);
    }

    @Test
    public void testVisitObject() {
        var visitor = new MockVisitor();
        Inspector inspector = new Slime().setObject();
        inspector.accept(visitor);
        assertEquals(MockVisitor.Called.OBJECT, visitor.called);
        assertSame(inspector, visitor.stuff);
    }
}
