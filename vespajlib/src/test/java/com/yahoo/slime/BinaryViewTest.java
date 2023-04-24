// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static com.yahoo.slime.BinaryFormat.encode_type_and_meta;

public class BinaryViewTest {
    static String makeString(int size) {
        var str = new StringBuilder();
        for (int i = 0; i < size; ++i) {
            str.append("A");
        }
        return str.toString();
    }
    static byte[] makeData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; ++i) {
            data[i] = 65;
        }
        return data;
    }
    static final int numLeafs = 22;
    static Cursor insertLeaf(Inserter dst, int id) {
        return switch (id) {
        case  0 -> dst.insertNIX();
        case  1 -> dst.insertBOOL(false);
        case  2 -> dst.insertBOOL(true);
        case  3 -> dst.insertLONG(42L);
        case  4 -> dst.insertLONG(-42L);
        case  5 -> dst.insertLONG(0x1234_5678_8765_4321L);
        case  6 -> dst.insertLONG(-0x1234_5678_8765_4321L);
        case  7 -> dst.insertDOUBLE(3.5);
        case  8 -> dst.insertDOUBLE(1.0/3.0);
        case  9 -> dst.insertDOUBLE(-3.5);
        case 10 -> dst.insertDOUBLE(-(1.0/3.0));
        case 11 -> dst.insertSTRING(makeString(5));
        case 12 -> dst.insertSTRING(makeString(50));
        case 13 -> dst.insertSTRING(makeString(300));
        case 14 -> dst.insertSTRING(makeData(5));
        case 15 -> dst.insertSTRING(makeData(50));
        case 16 -> dst.insertSTRING(makeData(300));
        case 17 -> dst.insertDATA(makeData(5));
        case 18 -> dst.insertDATA(makeData(50));
        case 19 -> dst.insertDATA(makeData(300));
        case 20 -> dst.insertARRAY();
        case 21 -> dst.insertOBJECT();
        default -> NixValue.invalid();
        };
    }
    static Cursor insertInnerObject(Inserter dst) {
        var obj = dst.insertOBJECT();
        for (int i = 0; i < numLeafs; ++i) {
            assertTrue(insertLeaf(new ObjectInserter(obj, "leaf" + i), i).valid());
        }
        return obj;
    }
    static Cursor insertInnerArray(Inserter dst) {
        var arr = dst.insertARRAY();
        for (int i = 0; i < numLeafs; ++i) {
            assertTrue(insertLeaf(new ArrayInserter(arr), i).valid());
        }
        return arr;
    }
    static Cursor insertOuterObject(Inserter dst) {
        var obj = dst.insertOBJECT();
        assertTrue(insertInnerObject(new ObjectInserter(obj, "foo")).valid());
        assertTrue(insertInnerArray(new ObjectInserter(obj, "bar")).valid());
        return obj;
    }
    static Cursor insertOuterArray(Inserter dst) {
        var arr = dst.insertARRAY();
        assertTrue(insertInnerObject(new ArrayInserter(arr)).valid());
        assertTrue(insertInnerArray(new ArrayInserter(arr)).valid());
        return arr;
    }
    static Cursor insertManySymbols(Inserter dst) {
        var obj = dst.insertOBJECT();
        for (int i = 0; i < 300; ++i) {
            obj.setLong("val" + i, i);
        }
        assertEquals(300, obj.fields());
        return obj;
    }
    static Cursor insertLargeArray(Inserter dst) {
        var arr = dst.insertARRAY();
        for (int i = 0; i < 300; ++i) {
            arr.addLong(i);
        }
        assertEquals(300, arr.entries());
        return arr;
    }
    static Cursor insert10SimpleHits(Inserter dst) {
        var arr = dst.insertARRAY();
        for (int i = 0; i < 10; ++i) {
            var obj = arr.addObject();
            obj.setLong("id", 123456);
        }
        return arr;
    }
    static final int numShapes = numLeafs + 7;
    static Cursor insertRoot(Slime dst, int shape) {
        var root = new SlimeInserter(dst);
        if (shape < numLeafs) {
            return insertLeaf(root, shape);
        }
        return switch (shape) {
        case (numLeafs) -> insertInnerObject(root);
        case (numLeafs + 1) -> insertInnerArray(root);
        case (numLeafs + 2) -> insertOuterObject(root);
        case (numLeafs + 3) -> insertOuterArray(root); 
        case (numLeafs + 4) -> insertManySymbols(root);
        case (numLeafs + 5) -> insertLargeArray(root);
        case (numLeafs + 6) -> insert10SimpleHits(root);
        default -> NixValue.invalid();
        };
    }
    static Slime makeSlime(int shape) {
        var slime = new Slime();
        var root = insertRoot(slime, shape);
        assertTrue(root.valid());
        return slime;
    }

    class MyConsumer implements Consumer<Inspector> {
        Inspector value = null;
        @Override public void accept(Inspector value) {
            assertNull(ctx, this.value);
            this.value = value;
        }
    };

    void checkConsumer(Inspector view) {
        var consumer = new MyConsumer();
        view.ifValid(consumer);
        assertEquals(ctx, view.valid(), consumer.value != null);
        if (view.valid()) {
            assertSame(ctx, view, consumer.value);
        }
    }

    void checkVisitor(Inspector view) {
        var visitor = new MockVisitor(ctx);
        view.accept(visitor);
        if (!view.valid()) {
            assertEquals(ctx, MockVisitor.Called.INVALID, visitor.called);
            return;
        }
        switch (view.type()) {
            case NIX:
                assertEquals(ctx, MockVisitor.Called.NIX, visitor.called);
                break;
            case BOOL:
                assertEquals(ctx, MockVisitor.Called.BOOL, visitor.called);
                assertEquals(ctx, view.asBool(), visitor.boolValue);
                break;
            case LONG:
                assertEquals(ctx, MockVisitor.Called.LONG, visitor.called);
                assertEquals(ctx, view.asLong(), visitor.longValue);
                break;
            case DOUBLE:
                assertEquals(ctx, MockVisitor.Called.DOUBLE, visitor.called);
                assertEquals(ctx, view.asDouble(), visitor.doubleValue, 0.0);
                break;
            case STRING:
                assertEquals(ctx, MockVisitor.Called.UTF8, visitor.called);
                assertArrayEquals(ctx, view.asUtf8(), visitor.bytes);
                break;
            case DATA:
                assertEquals(ctx, MockVisitor.Called.DATA, visitor.called);
                assertArrayEquals(ctx, view.asData(), visitor.bytes);
                break;
            case ARRAY:
                assertEquals(ctx, MockVisitor.Called.ARRAY, visitor.called);
                assertSame(ctx, view, visitor.stuff);
                break;
            case OBJECT:
                assertEquals(ctx, MockVisitor.Called.OBJECT, visitor.called);
                assertSame(ctx, view, visitor.stuff);
                break;
            default:
                fail(ctx + ", should not be reached");
                break;
        }
    }

    class MyArrayTraverser implements ArrayTraverser {
        ArrayList<Inspector> list = new ArrayList<>();
        @Override public void entry(int idx, Inspector value) {
            list.add(value);
        }
    }

    void checkTraverseArray(Inspector value, Inspector view) {
        var a = new MyArrayTraverser();
        var b = new MyArrayTraverser();
        value.traverse(a);
        view.traverse(b);
        assertEquals(ctx, a.list.size(), b.list.size());
        for (int i = 0; i < a.list.size(); ++i) {
            checkParity(a.list.get(i), b.list.get(i));
        }
    }

    class MyObjectSymbolTraverser implements ObjectSymbolTraverser {
        HashMap<Integer,Inspector> map = new HashMap<>();
        @Override public void field(int sym, Inspector value) {
            map.put(sym, value);
        }
    }

    void checkTraverseObjectSymbol(Inspector value, Inspector view) {
        var a = new MyObjectSymbolTraverser();
        var b = new MyObjectSymbolTraverser();
        value.traverse(a);
        view.traverse(b);
        assertEquals(ctx, a.map.size(), b.map.size());
        for (Integer key: a.map.keySet()) {
            assertTrue(ctx, b.map.containsKey(key));
            checkParity(a.map.get(key), b.map.get(key));
        }
    }

    class MyObjectTraverser implements ObjectTraverser {
        HashMap<String,Inspector> map = new HashMap<>();
        @Override public void field(String name, Inspector value) {
            map.put(name, value);
        }
    }

    void checkTraverseObject(Inspector value, Inspector view) {
        var a = new MyObjectTraverser();
        var b = new MyObjectTraverser();
        value.traverse(a);
        view.traverse(b);
        assertEquals(ctx, a.map.size(), b.map.size());
        for (String key: a.map.keySet()) {
            assertTrue(ctx, b.map.containsKey(key));
            checkParity(a.map.get(key), b.map.get(key));
        }
    }
    void checkParity(Inspector value, Inspector view) {
        checkConsumer(view);
        checkVisitor(view);
        if (value == view) {
            // avoid infinite invalid nix recursion
            assertSame(ctx, value, view);
            return;
        }
        assertEquals(ctx, value.valid(), view.valid());
        assertEquals(ctx, value.type(), view.type());
        assertEquals(ctx, value.children(), view.children());
        assertEquals(ctx, value.entries(), view.entries());
        assertEquals(ctx, value.fields(), view.fields());
        assertEquals(ctx, value.asBool(), view.asBool());
        assertEquals(ctx, value.asLong(), view.asLong());
        assertEquals(ctx, value.asDouble(), view.asDouble(), 0.0);
        assertEquals(ctx, value.asString(), view.asString());
        assertArrayEquals(ctx, value.asUtf8(), view.asUtf8());
        assertArrayEquals(ctx, value.asData(), view.asData());
        checkTraverseArray(value, view);
        checkTraverseObjectSymbol(value, view);
        checkTraverseObject(value, view);
        checkParity(value.entry(0), view.entry(0));
        checkParity(value.entry(1), view.entry(1));
        checkParity(value.entry(2), view.entry(2));
        checkParity(value.entry(3), view.entry(3));
        checkParity(value.entry(200), view.entry(200));
        checkParity(value.entry(500), view.entry(500));
        checkParity(value.entry(-1), view.entry(-1));
        checkParity(value.field(0), view.field(0));
        checkParity(value.field(1), view.field(1));
        checkParity(value.field(2), view.field(2));
        checkParity(value.field(3), view.field(3));
        checkParity(value.field(SymbolTable.INVALID), view.field(SymbolTable.INVALID));
        checkParity(value.field(-1), view.field(-1));
        checkParity(value.field("foo"), view.field("foo"));
        checkParity(value.field("bar"), view.field("bar"));
        checkParity(value.field("val256"), view.field("val256"));
        checkParity(value.field("bogus"), view.field("bogus"));
        assertTrue(ctx, value.equalTo(view));
        assertTrue(ctx, view.equalTo(value));
    }

    String ctx;
    @Test public void testBinaryViewShapesParity() {
        for (int i = 0; i < numShapes; ++i) {
            var slime = makeSlime(i);
            ctx = "case " + i + ": '" + slime.toString() + "'";
            byte[] data = BinaryFormat.encode(slime);
            try {
                checkParity(slime.get(), BinaryView.inspect(data));
            } catch (Exception e) {
                fail(ctx + ", got exception: " + e);
            }
        }
    }

    void assertFail(byte[] data, String reason) {
        try {
            var view = BinaryView.inspect(data);
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("bad input: " + reason, e.getMessage());
        }
    }

    @Test public void testTrivialView() {
        byte[] data = {0, 0};
        var view = BinaryView.inspect(data);
        assertTrue(view.valid());
        assertEquals(Type.NIX, view.type());
    }

    @Test public void testUnderflow() {
        byte[] data = {};
        assertFail(data, "underflow");
    }

    @Test public void testMultiByteUnderflow() {
        byte[] data = { 0, encode_type_and_meta(Type.STRING.ID, 3), 65 };
        assertFail(data, "underflow");
    }

    @Test public void testCompressedIntOverflow() {
        byte[] data = { -1, -1, -1, -1, 8 };
        assertFail(data, "compressed int overflow");
    }

    @Test public void testExtBitsOverflow() {
        byte[] data = { 0, encode_type_and_meta(Type.OBJECT.ID, 2), -1, -1, -1, -1, 1 };
        assertFail(data, "symbol id too big");
    }

    @Test public void testDecodeIndexOverflowArray() {
        byte[] data = { 0, encode_type_and_meta(Type.ARRAY.ID, 20) };
        assertFail(data, "decode index too big");
    }

    @Test public void testDecodeIndexOverflowObject() {
        byte[] data = { 0, encode_type_and_meta(Type.OBJECT.ID, 20) };
        assertFail(data, "decode index too big");
    }
}
