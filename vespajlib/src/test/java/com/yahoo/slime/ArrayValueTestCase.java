// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotSame;

import java.util.List;
import java.util.ArrayList;

public class ArrayValueTestCase {

    static ArrayValue makeArray() {
        return new ArrayValue(new SymbolTable());
    }

    @Test
    public void testSymbolTableForwarding() {
        SymbolTable names = new SymbolTable();
        assertEquals(0, names.symbols());
        new ArrayValue(names).addArray().addObject().setLong("foo", 3);
        assertEquals(1, names.symbols());
    }

    @Test
    public void testOutOfBoundsAccess() {
        var array = makeArray();
        array.addBool(true);
        assertFalse(array.entry(-1).valid());
        assertFalse(array.entry(1).valid());
    }

    @Test
    public void testGenericArray() {
        var array = makeArray();
        var added = new ArrayList<Cursor>();
        for (int i = 0; i < 128; ++i) {
            added.add(array.addString("foo" + i));
        }
        for (int i = 0; i < 128; i++) {
            var e1 = array.entry(i);
            var e2 = array.entry(i);
            var e3 = added.get(i);
            assertSame(e2, e1);
            assertSame(e3, e1);
        }
    }

    @Test
    public void testNativeLongArray() {
        var array = makeArray();
        var added = new ArrayList<Cursor>();
        for (int i = 0; i < 128; ++i) {
            added.add(array.addLong(i));
        }
        for (int i = 0; i < 128; ++i) {
            long expect = i;
            var e1 = array.entry(i);
            var e2 = array.entry(i);
            var e3 = added.get(i);
            assertNotSame(e2, e1);
            assertNotSame(e3, e1);
            assertTrue(e1.equalTo(e2));
            assertTrue(e1.equalTo(e3));
            assertEquals(Type.LONG, e1.type());
            assertEquals(expect, e1.asLong());
        }
    }

    @Test
    public void testNativeDoubleArray() {
        var array = makeArray();
        var added = new ArrayList<Cursor>();
        for (int i = 0; i < 128; ++i) {
            added.add(array.addDouble((double)i));
        }
        for (int i = 0; i < 128; ++i) {
            double expect = i;
            var e1 = array.entry(i);
            var e2 = array.entry(i);
            var e3 = added.get(i);
            assertNotSame(e2, e1);
            assertNotSame(e3, e1);
            assertTrue(e1.equalTo(e2));
            assertTrue(e1.equalTo(e3));
            assertEquals(Type.DOUBLE, e1.type());
            assertEquals(expect, e1.asDouble(), 0.0);
        }
    }

    @Test
    public void testLongToGenericConversion() {
        for (Type type: Type.values()) {
            if (type != Type.LONG) {
                var array = makeArray();
                var added = new ArrayList<Cursor>();
                for (int i = 0; i < 64; ++i) {
                    added.add(array.addLong(i));
                }
                switch (type) {
                case NIX: added.add(array.addNix()); break;
                case BOOL: added.add(array.addBool(true)); break;
                case DOUBLE: added.add(array.addDouble(42.0)); break;
                case STRING: added.add(array.addString("foo")); break;
                case DATA: added.add(array.addData(new byte[1])); break;
                case ARRAY: added.add(array.addArray()); break;
                case OBJECT: added.add(array.addObject()); break;
                }
                assertEquals(65, array.entries());
                assertEquals(type, array.entry(64).type());
                assertSame(array.entry(64), added.get(64));
                for (int i = 0; i < 64; ++i) {
                    var e1 = array.entry(i);
                    var e2 = array.entry(i);
                    var e3 = added.get(i);
                    long expect = i;
                    assertSame(e2, e1);
                    assertNotSame(e3, e1);
                    assertTrue(e1.equalTo(e2));
                    assertTrue(e1.equalTo(e3));
                    assertEquals(Type.LONG, e1.type());
                    assertEquals(expect, e1.asLong());
                }
            }
        }
    }

    @Test
    public void testDoubleToGenericConversion() {
        for (Type type: Type.values()) {
            if (type != Type.DOUBLE) {
                var array = makeArray();
                var added = new ArrayList<Cursor>();
                for (int i = 0; i < 64; ++i) {
                    added.add(array.addDouble(i));
                }
                switch (type) {
                case NIX: added.add(array.addNix()); break;
                case BOOL: added.add(array.addBool(true)); break;
                case LONG: added.add(array.addLong(42)); break;
                case STRING: added.add(array.addString("foo")); break;
                case DATA: added.add(array.addData(new byte[1])); break;
                case ARRAY: added.add(array.addArray()); break;
                case OBJECT: added.add(array.addObject()); break;
                }
                assertEquals(65, array.entries());
                assertEquals(type, array.entry(64).type());
                assertSame(array.entry(64), added.get(64));
                for (int i = 0; i < 64; ++i) {
                    var e1 = array.entry(i);
                    var e2 = array.entry(i);
                    var e3 = added.get(i);
                    double expect = i;
                    assertSame(e2, e1);
                    assertNotSame(e3, e1);
                    assertTrue(e1.equalTo(e2));
                    assertTrue(e1.equalTo(e3));
                    assertEquals(Type.DOUBLE, e1.type());
                    assertEquals(expect, e1.asDouble(), 0.0);
                }
            }
        }
    }

    @Test
    public void testGenericArrayStart() {
        for (Type type: Type.values()) {
            if (type != Type.LONG && type != Type.DOUBLE) {
                var array = makeArray();
                Cursor added = null;
                switch (type) {
                case NIX: added = array.addNix(); break;
                case BOOL: added = array.addBool(true); break;
                case STRING: added = array.addString("foo"); break;
                case DATA: added = array.addData(new byte[1]); break;
                case ARRAY: added = array.addArray(); break;
                case OBJECT: added = array.addObject(); break;
                }
                assertEquals(1, array.entries());
                assertEquals(type, array.entry(0).type());
                assertSame(array.entry(0), added);
            }
        }
    }
}
