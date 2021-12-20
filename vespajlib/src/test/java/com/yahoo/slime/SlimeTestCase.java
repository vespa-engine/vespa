// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;

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

        assertThat(Type.NIX.ID,    is((byte)0));
        assertThat(Type.BOOL.ID,   is((byte)1));
        assertThat(Type.LONG.ID,   is((byte)2));
        assertThat(Type.DOUBLE.ID, is((byte)3));
        assertThat(Type.STRING.ID, is((byte)4));
        assertThat(Type.DATA.ID,   is((byte)5));
        assertThat(Type.ARRAY.ID,  is((byte)6));
        assertThat(Type.OBJECT.ID, is((byte)7));

        assertThat(Type.values().length, is(8));

        assertThat(Type.values()[0], sameInstance(Type.NIX));
        assertThat(Type.values()[1], sameInstance(Type.BOOL));
        assertThat(Type.values()[2], sameInstance(Type.LONG));
        assertThat(Type.values()[3], sameInstance(Type.DOUBLE));
        assertThat(Type.values()[4], sameInstance(Type.STRING));
        assertThat(Type.values()[5], sameInstance(Type.DATA));
        assertThat(Type.values()[6], sameInstance(Type.ARRAY));
        assertThat(Type.values()[7], sameInstance(Type.OBJECT));
    }

    @Test
    public void testEmpty() {
        System.out.println("testing empty slime...");
        Slime slime = new Slime();
        Cursor cur;
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                cur = slime.get();
                assertThat(cur.valid(), is(true));
            } else {
                cur = NixValue.invalid();
                assertThat(cur.valid(), is(false));
            }
            assertThat(cur.type(), is(Type.NIX));
            assertThat(cur.children(), is(0));
            assertThat(cur.asBool(), is(false));
            assertThat(cur.asLong(), is((long)0));
            assertThat(cur.asDouble(), is(0.0));
            assertThat(cur.asString(), is(""));
            assertThat(cur.asData(), is(new byte[0]));
            assertThat(cur.entry(0).valid(), is(false));
            assertThat(cur.field(0).valid(), is(false));
            assertThat(cur.field("foo").valid(), is(false));
        }
        Inspector insp;
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                insp = slime.get();
                assertThat(insp.valid(), is(true));
            } else {
                insp = NixValue.invalid();
                assertThat(insp.valid(), is(false));
            }
            assertThat(insp.type(), is(Type.NIX));
            assertThat(insp.children(), is(0));
            assertThat(insp.asBool(), is(false));
            assertThat(insp.asLong(), is((long)0));
            assertThat(insp.asDouble(), is(0.0));
            assertThat(insp.asString(), is(""));
            assertThat(insp.asData(), is(new byte[0]));
            assertThat(insp.entry(0).valid(), is(false));
            assertThat(insp.field(0).valid(), is(false));
            assertThat(insp.field("foo").valid(), is(false));
        }
    }

    @Test
    public void testBasic() {
        System.out.println("testing basic values...");
        Slime slime = new Slime();

        System.out.println("testing boolean value");
        slime.setBool(true);
        Inspector insp = slime.get();
        assertThat(insp.valid(), is(true));
        assertThat(insp.type(), sameInstance(Type.BOOL));
        assertThat(insp.asBool(), is(true));
        Cursor cur = slime.get();
        assertThat(cur.valid(), is(true));
        assertThat(cur.type(), sameInstance(Type.BOOL));
        assertThat(cur.asBool(), is(true));

        System.out.println("testing long value");
        slime.setLong(42);
        cur = slime.get();
        insp = slime.get();
        assertThat(cur.valid(), is(true));
        assertThat(insp.valid(), is(true));
        assertThat(cur.type(), sameInstance(Type.LONG));
        assertThat(insp.type(), sameInstance(Type.LONG));
        assertThat(cur.asLong(), is((long)42));
        assertThat(insp.asLong(), is((long)42));

        System.out.println("testing double value");
        slime.setDouble(4.2);
        cur = slime.get();
        insp = slime.get();
        assertThat(cur.valid(), is(true));
        assertThat(insp.valid(), is(true));
        assertThat(cur.type(), sameInstance(Type.DOUBLE));
        assertThat(insp.type(), sameInstance(Type.DOUBLE));
        assertThat(cur.asDouble(), is(4.2));
        assertThat(insp.asDouble(), is(4.2));

        System.out.println("testing string value");
        slime.setString("fortytwo");
        cur = slime.get();
        insp = slime.get();
        assertThat(cur.valid(), is(true));
        assertThat(insp.valid(), is(true));
        assertThat(cur.type(), sameInstance(Type.STRING));
        assertThat(insp.type(), sameInstance(Type.STRING));
        assertThat(cur.asString(), is("fortytwo"));
        assertThat(insp.asString(), is("fortytwo"));

        System.out.println("testing data value");
        byte[] data = { (byte)4, (byte)2 };
        slime.setData(data);
        cur = slime.get();
        insp = slime.get();
        assertThat(cur.valid(), is(true));
        assertThat(insp.valid(), is(true));
        assertThat(cur.type(), sameInstance(Type.DATA));
        assertThat(insp.type(), sameInstance(Type.DATA));
        assertThat(cur.asData(), is(data));
        assertThat(insp.asData(), is(data));
        data[0] = 10;
        data[1] = 20;
        byte[] data2 = { 10, 20 };
        assertThat(cur.asData(), is(data2));
        assertThat(insp.asData(), is(data2));
    }

    @Test
    public void testArray() {
        System.out.println("testing array values...");
        Slime slime = new Slime();
        Cursor c = slime.setArray();
        assertThat(c.valid(), is(true));
        assertThat(c.type(), is(Type.ARRAY));
        assertThat(c.children(), is(0));
        Inspector i = slime.get();
        assertThat(i.valid(), is(true));
        assertThat(i.type(), is(Type.ARRAY));
        assertThat(i.children(), is(0));
        c.addNix();
        c.addBool(true);
        c.addLong(5);
        c.addDouble(3.5);
        c.addString("string");
        byte[] data = { (byte)'d', (byte)'a', (byte)'t', (byte)'a' };
        c.addData(data);
        assertThat(c.children(), is(6));
        assertThat(c.entry(0).valid(), is(true));
        assertThat(c.entry(1).asBool(), is(true));
        assertThat(c.entry(2).asLong(), is((long)5));
        assertThat(c.entry(3).asDouble(), is(3.5));
        assertThat(c.entry(4).asString(), is("string"));
        assertThat(c.entry(5).asData(), is(data));
        assertThat(c.field(5).valid(), is(false)); // not OBJECT

        assertThat(i.children(), is(6));
        assertThat(i.entry(0).valid(), is(true));
        assertThat(i.entry(1).asBool(), is(true));
        assertThat(i.entry(2).asLong(), is((long)5));
        assertThat(i.entry(3).asDouble(), is(3.5));
        assertThat(i.entry(4).asString(), is("string"));
        assertThat(i.entry(5).asData(), is(data));
        assertThat(i.field(5).valid(), is(false)); // not OBJECT
    }

    @Test
    public void testObject() {
        System.out.println("testing object values...");
        Slime slime = new Slime();
        Cursor c = slime.setObject();

        assertThat(c.valid(), is(true));
        assertThat(c.type(), is(Type.OBJECT));
        assertThat(c.children(), is(0));
        Inspector i = slime.get();
        assertThat(i.valid(), is(true));
        assertThat(i.type(), is(Type.OBJECT));
        assertThat(i.children(), is(0));

        c.setNix("a");
        c.setBool("b", true);
        c.setLong("c", 5);
        c.setDouble("d", 3.5);
        c.setString("e", "string");
        byte[] data = { (byte)'d', (byte)'a', (byte)'t', (byte)'a' };
        c.setData("f", data);

        assertThat(c.children(), is(6));
        assertThat(c.field("a").valid(), is(true));
        assertThat(c.field("b").asBool(), is(true));
        assertThat(c.field("c").asLong(), is((long)5));
        assertThat(c.field("d").asDouble(), is(3.5));
        assertThat(c.field("e").asString(), is("string"));
        assertThat(c.field("f").asData(), is(data));
        assertThat(c.entry(4).valid(), is(false)); // not ARRAY

        assertThat(i.children(), is(6));
        assertThat(i.field("a").valid(), is(true));
        assertThat(i.field("b").asBool(), is(true));
        assertThat(i.field("c").asLong(), is((long)5));
        assertThat(i.field("d").asDouble(), is(3.5));
        assertThat(i.field("e").asString(), is("string"));
        assertThat(i.field("f").asData(), is(data));
        assertThat(i.entry(4).valid(), is(false)); // not ARRAY
    }

    @Test
    public void testChaining() {
        System.out.println("testing cursor chaining...");
        {
            Slime slime = new Slime();
            Cursor c = slime.setArray();
            assertThat(c.addLong(5).asLong(), is((long)5));
        }
        {
            Slime slime = new Slime();
            Cursor c = slime.setObject();
            assertThat(c.setLong("a", 5).asLong(), is((long)5));
        }
    }

    @Test
    public void testCursorToInspector() {
        System.out.println("testing proxy conversion...");

        Slime slime = new Slime();
        Cursor c = slime.setLong(10);
        Inspector i1 = c;
        assertThat(i1.asLong(), is((long)10));

        Inspector i2 = slime.get();
        assertThat(i2.asLong(), is((long)10));
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
        assertThat(i.field("bar").asLong(), is((long)10));
        assertThat(i.field("foo").entry(0).asLong(), is((long)20));
        assertThat(i.field("foo").entry(1).field("answer").asLong(), is((long)42));

        Cursor c = slime.get();
        assertThat(c.field("bar").asLong(), is((long)10));
        assertThat(c.field("foo").entry(0).asLong(), is((long)20));
        assertThat(c.field("foo").entry(1).field("answer").asLong(), is((long)42));
    }

    @Test
    public void testLotsOfSymbolsAndFields() {
        // put pressure on symbol table and object fields
        int n = 1000;
        Slime slime = new Slime();
        Cursor c = slime.setObject();
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertThat(slime.lookup(str), is(SymbolTable.INVALID));
            assertThat(c.field(str).type(), sameInstance(Type.NIX));
            switch (i % 2) {
            case 0: assertThat((int)c.setLong(str, i).asLong(), is(i)); break;
            case 1: assertThat(slime.insert(str), is(i)); break;
            }
        }
        for (int i = 0; i < n; i++) {
            String str = ("" + i + "_str_" + i);
            assertThat(slime.lookup(str), is(i));
            switch (i % 2) {
            case 0: assertThat((int)c.field(str).asLong(), is(i)); break;
            case 1: assertThat((int)c.field(str).asLong(), is(0)); break;
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
            assertThat((int)c.addLong(i).asLong(), is(i));
        }
        for (int i = 0; i < n; i++) {
            assertThat((int)c.entry(i).asLong(), is(i));
        }
        assertThat((int)c.entry(n).asLong(), is(0));
    }

    @Test
    public void testToString() {
        Slime slime = new Slime();
        Cursor c1 = slime.setArray();
        c1.addLong(20);
        Cursor c2 = c1.addObject();
        c2.setLong("answer", 42);
        assertThat(slime.get().toString(), is("[20,{\"answer\":42}]"));
        c1.addString("\u2008");
        assertThat(slime.get().toString(), is("[20,{\"answer\":42},\"\u2008\"]"));
    }
}
