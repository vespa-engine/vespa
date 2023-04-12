// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SlimeTestCase {

    @Test
    public void lul() throws Exception {
        Slime s = new Slime();
        Cursor c = s.setObject();
        c.setObject("h").setObject("h").setObject("h").setString("h", "a");
        new JsonFormat(false).encode(System.err, s);
    }

    @Test
    public void testTypeIds() {
        System.out.println("testing type identifiers...");

        assertEquals((byte)0, Type.NIX.ID);
        assertEquals((byte)1, Type.BOOL.ID);
        assertEquals((byte)2, Type.LONG.ID);
        assertEquals((byte)3, Type.DOUBLE.ID);
        assertEquals((byte)4, Type.STRING.ID);
        assertEquals((byte)5, Type.DATA.ID);
        assertEquals((byte)6, Type.ARRAY.ID);
        assertEquals((byte)7, Type.OBJECT.ID);

        assertEquals(8, Type.values().length);

        assertSame(Type.NIX, Type.values()[0]);
        assertSame(Type.BOOL, Type.values()[1]);
        assertSame(Type.LONG, Type.values()[2]);
        assertSame(Type.DOUBLE, Type.values()[3]);
        assertSame(Type.STRING, Type.values()[4]);
        assertSame(Type.DATA, Type.values()[5]);
        assertSame(Type.ARRAY, Type.values()[6]);
        assertSame(Type.OBJECT, Type.values()[7]);
    }

    @Test
    public void testEmpty() {
        System.out.println("testing empty slime...");
        Slime slime = new Slime();
        Cursor cur;
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                cur = slime.get();
                assertTrue(cur.valid());
            } else {
                cur = NixValue.invalid();
                assertFalse(cur.valid());
            }
            assertEquals(Type.NIX, cur.type());
            assertEquals(0, cur.children());
            assertFalse(cur.asBool());
            assertEquals(0L, cur.asLong());
            assertEquals(0.0, cur.asDouble(), 0.0);
            assertEquals("", cur.asString());
            assertArrayEquals(new byte[0], cur.asData());
            assertFalse(cur.entry(0).valid());
            assertFalse(cur.field(0).valid());
            assertFalse(cur.field("foo").valid());
        }
        Inspector insp;
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                insp = slime.get();
                assertTrue(insp.valid());
            } else {
                insp = NixValue.invalid();
                assertFalse(insp.valid());
            }
            assertEquals(Type.NIX, insp.type());
            assertEquals(0, insp.children());
            assertFalse(insp.asBool());
            assertEquals(0L, insp.asLong());
            assertEquals(0.0, insp.asDouble(), 0.0);
            assertEquals("", insp.asString());
            assertArrayEquals(new byte[0], insp.asData());
            assertFalse(insp.entry(0).valid());
            assertFalse(insp.field(0).valid());
            assertFalse(insp.field("foo").valid());
        }
    }

    @Test
    public void testBasic() {
        System.out.println("testing basic values...");
        Slime slime = new Slime();

        System.out.println("testing boolean value");
        slime.setBool(true);
        Inspector insp = slime.get();
        assertTrue(insp.valid());
        assertSame(Type.BOOL, insp.type());
        assertTrue(insp.asBool());
        Cursor cur = slime.get();
        assertTrue(cur.valid());
        assertSame(Type.BOOL, cur.type());
        assertTrue(cur.asBool());

        System.out.println("testing long value");
        slime.setLong(42);
        cur = slime.get();
        insp = slime.get();
        assertTrue(cur.valid());
        assertTrue(insp.valid());
        assertSame(Type.LONG, cur.type());
        assertSame(Type.LONG, insp.type());
        assertEquals(42L, cur.asLong());
        assertEquals(42L, insp.asLong());

        System.out.println("testing double value");
        slime.setDouble(4.2);
        cur = slime.get();
        insp = slime.get();
        assertTrue(cur.valid());
        assertTrue(insp.valid());
        assertSame(Type.DOUBLE, cur.type());
        assertSame(Type.DOUBLE, insp.type());
        assertEquals(4.2, cur.asDouble(), 0.0);
        assertEquals(4.2, insp.asDouble(), 0.0);

        System.out.println("testing string value");
        slime.setString("fortytwo");
        cur = slime.get();
        insp = slime.get();
        assertTrue(cur.valid());
        assertTrue(insp.valid());
        assertSame(Type.STRING, cur.type());
        assertSame(Type.STRING, insp.type());
        assertEquals("fortytwo", cur.asString());
        assertEquals("fortytwo", insp.asString());

        System.out.println("testing data value");
        byte[] data = { (byte)4, (byte)2 };
        slime.setData(data);
        cur = slime.get();
        insp = slime.get();
        assertTrue(cur.valid());
        assertTrue(insp.valid());
        assertSame(Type.DATA, cur.type());
        assertSame(Type.DATA, insp.type());
        assertArrayEquals(data, cur.asData());
        assertArrayEquals(data, insp.asData());
        data[0] = 10;
        data[1] = 20;
        byte[] data2 = { 10, 20 };
        assertArrayEquals(data2, cur.asData());
        assertArrayEquals(data2, insp.asData());
    }

    @Test
    public void testArray() {
        System.out.println("testing array values...");
        Slime slime = new Slime();
        Cursor c = slime.setArray();
        assertTrue(c.valid());
        assertEquals(Type.ARRAY, c.type());
        assertEquals(0, c.children());
        Inspector i = slime.get();
        assertTrue(i.valid());
        assertEquals(Type.ARRAY, i.type());
        assertEquals(0, i.children());
        c.addNix();
        c.addBool(true);
        c.addLong(5);
        c.addDouble(3.5);
        c.addString("string");
        byte[] data = { (byte)'d', (byte)'a', (byte)'t', (byte)'a' };
        c.addData(data);
        assertEquals(6, c.children());
        assertTrue(c.entry(0).valid());
        assertTrue(c.entry(1).asBool());
        assertEquals(5L, c.entry(2).asLong());
        assertEquals(3.5, c.entry(3).asDouble(), 0.0);
        assertEquals("string", c.entry(4).asString());
        assertArrayEquals(data, c.entry(5).asData());
        assertFalse(c.field(5).valid()); // not OBJECT

        assertEquals(6, i.children());
        assertTrue(i.entry(0).valid());
        assertTrue(i.entry(1).asBool());
        assertEquals(5L, i.entry(2).asLong());
        assertEquals(3.5, i.entry(3).asDouble(), 0.0);
        assertEquals("string", i.entry(4).asString());
        assertArrayEquals(data, i.entry(5).asData());
        assertFalse(i.field(5).valid()); // not OBJECT
    }

    @Test
    public void testObject() {
        System.out.println("testing object values...");
        Slime slime = new Slime();
        Cursor c = slime.setObject();

        assertTrue(c.valid());
        assertEquals(Type.OBJECT, c.type());
        assertEquals(0, c.children());
        Inspector i = slime.get();
        assertTrue(i.valid());
        assertEquals(Type.OBJECT, i.type());
        assertEquals(0, i.children());

        c.setNix("a");
        c.setBool("b", true);
        c.setLong("c", 5);
        c.setDouble("d", 3.5);
        c.setString("e", "string");
        byte[] data = { (byte)'d', (byte)'a', (byte)'t', (byte)'a' };
        c.setData("f", data);

        assertEquals(6, c.children());
        assertTrue(c.field("a").valid());
        assertTrue(c.field("b").asBool());
        assertEquals(5L, c.field("c").asLong());
        assertEquals(3.5, c.field("d").asDouble(), 0.0);
        assertEquals("string", c.field("e").asString());
        assertArrayEquals(data, c.field("f").asData());
        assertFalse(c.entry(4).valid()); // not ARRAY

        assertEquals(6, i.children());
        assertTrue(i.field("a").valid());
        assertTrue(i.field("b").asBool());
        assertEquals(5L, i.field("c").asLong());
        assertEquals(3.5, i.field("d").asDouble(), 0.0);
        assertEquals("string", i.field("e").asString());
        assertArrayEquals(data, i.field("f").asData());
        assertFalse(i.entry(4).valid()); // not ARRAY
    }

    @Test
    public void testChaining() {
        System.out.println("testing cursor chaining...");
        {
            Slime slime = new Slime();
            Cursor c = slime.setArray();
            assertEquals(5L, c.addLong(5).asLong());
        }
        {
            Slime slime = new Slime();
            Cursor c = slime.setObject();
            assertEquals(5L, c.setLong("a", 5).asLong());
        }
    }

    @Test
    public void testCursorToInspector() {
        System.out.println("testing proxy conversion...");

        Slime slime = new Slime();
        Cursor c = slime.setLong(10);
        Inspector i1 = c;
        assertEquals(10L, i1.asLong());

        Inspector i2 = slime.get();
        assertEquals(10L, i2.asLong());
    }

    @Test
    public void testNesting() {
        System.out.println("testing data nesting...");
        Slime slime = new Slime();
        {
            Cursor c1 = slime.setObject();
            c1.setLong("bar", 10);
            Cursor c2 = c1.setArray("foo");
            c2.addLong(20);
            Cursor c3 = c2.addObject();
            c3.setLong("answer", 42);
        }
        Inspector i = slime.get();
        assertEquals(10L, i.field("bar").asLong());
        assertEquals(20L, i.field("foo").entry(0).asLong());
        assertEquals(42L, i.field("foo").entry(1).field("answer").asLong());

        Cursor c = slime.get();
        assertEquals(10L, c.field("bar").asLong());
        assertEquals(20L, c.field("foo").entry(0).asLong());
        assertEquals(42L, c.field("foo").entry(1).field("answer").asLong());
    }

    @Test
    public void testLotsOfSymbolsAndFields() {
        // put pressure on symbol table and object fields
        int n = 1000;
        Slime slime = new Slime();
        Cursor c = slime.setObject();
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertEquals(SymbolTable.INVALID, slime.lookup(str));
            assertSame(Type.NIX, c.field(str).type());
            switch (i % 2) {
                case 0: assertEquals(i, (int)c.setLong(str, i).asLong()); break;
                case 1: assertEquals(i, slime.insert(str)); break;
            }
        }
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertEquals(i, slime.lookup(str));
            switch (i % 2) {
                case 0: assertEquals(i, (int)c.field(str).asLong()); break;
                case 1: assertEquals(0, (int)c.field(str).asLong()); break;
            }
        }
    }

    @Test
    public void testLotsOfEntries() {
        // put pressure on array entries
        int n = 1000;
        Slime slime = new Slime();
        Cursor c = slime.setArray();
        for (int i = 0; i < n; i++) {
            assertEquals(i, (int)c.addLong(i).asLong());
        }
        for (int i = 0; i < n; i++) {
            assertEquals(i, (int)c.entry(i).asLong());
        }
        assertEquals(0, (int)c.entry(n).asLong());
    }

    @Test
    public void testToString() {
        Slime slime = new Slime();
        Cursor c1 = slime.setArray();
        c1.addLong(20);
        Cursor c2 = c1.addObject();
        c2.setLong("answer", 42);
        assertEquals("[20,{\"answer\":42}]", slime.get().toString());
        c1.addString("\u2008");
        assertEquals("[20,{\"answer\":42},\"\u2008\"]", slime.get().toString());
    }
}
