// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;

import java.util.List;
import java.util.ArrayList;

public class ArrayValueTestCase {

    static ArrayValue makeArray() {
        return new ArrayValue(new SymbolTable());
    }

    @Test
    public void testSymbolTableForwarding() {
        SymbolTable names = new SymbolTable();
        assertThat(names.symbols(), is(0));
        new ArrayValue(names).addArray().addObject().setLong("foo", 3);
        assertThat(names.symbols(), is(1));
    }

    @Test
    public void testOutOfBoundsAccess() {
        var array = makeArray();
        array.addBool(true);
        assertThat(array.entry(-1).valid(), is(false));
        assertThat(array.entry(1).valid(), is(false));
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
            assertThat(e1, sameInstance(e2));
            assertThat(e1, sameInstance(e3));
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
            assertThat(e1, not(sameInstance(e2)));
            assertThat(e1, not(sameInstance(e3)));
            assertThat(e1.equalTo(e2), is(true));
            assertThat(e1.equalTo(e3), is(true));
            assertThat(e1.type(), is(Type.LONG));
            assertThat(e1.asLong(), is(expect));
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
            assertThat(e1, not(sameInstance(e2)));
            assertThat(e1, not(sameInstance(e3)));
            assertThat(e1.equalTo(e2), is(true));
            assertThat(e1.equalTo(e3), is(true));
            assertThat(e1.type(), is(Type.DOUBLE));
            assertThat(e1.asDouble(), is(expect));
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
                assertThat(array.entries(), is(65));
                assertThat(array.entry(64).type(), is(type));
                assertThat(added.get(64), sameInstance(array.entry(64)));
                for (int i = 0; i < 64; ++i) {
                    var e1 = array.entry(i);
                    var e2 = array.entry(i);
                    var e3 = added.get(i);
                    long expect = i;
                    assertThat(e1, sameInstance(e2));
                    assertThat(e1, not(sameInstance(e3)));
                    assertThat(e1.equalTo(e2), is(true));
                    assertThat(e1.equalTo(e3), is(true));
                    assertThat(e1.type(), is(Type.LONG));
                    assertThat(e1.asLong(), is(expect));
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
                assertThat(array.entries(), is(65));
                assertThat(array.entry(64).type(), is(type));
                assertThat(added.get(64), sameInstance(array.entry(64)));
                for (int i = 0; i < 64; ++i) {
                    var e1 = array.entry(i);
                    var e2 = array.entry(i);
                    var e3 = added.get(i);
                    double expect = i;
                    assertThat(e1, sameInstance(e2));
                    assertThat(e1, not(sameInstance(e3)));
                    assertThat(e1.equalTo(e2), is(true));
                    assertThat(e1.equalTo(e3), is(true));
                    assertThat(e1.type(), is(Type.DOUBLE));
                    assertThat(e1.asDouble(), is(expect));
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
                assertThat(array.entries(), is(1));
                assertThat(array.entry(0).type(), is(type));
                assertThat(added, sameInstance(array.entry(0)));
            }
        }
    }
}
