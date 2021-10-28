// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class InjectorTest {
    private static class SourceFixture {
        public final Slime empty = new Slime();
        public final Slime nixValue = new Slime();
        public final Slime boolValue = new Slime();
        public final Slime longValue = new Slime();
        public final Slime doubleValue = new Slime();
        public final Slime stringValue = new Slime();
        public final Slime dataValue = new Slime();
        public final Slime arrayValue = new Slime();
        public final Slime objectValue = new Slime();

        SourceFixture() {
            nixValue.setNix();
            boolValue.setBool(true);
            longValue.setLong(10);
            doubleValue.setDouble(20.0);
            stringValue.setString("string");
            dataValue.setData("data".getBytes(StandardCharsets.UTF_8));
            Cursor arr = arrayValue.setArray();
            arr.addLong(1);
            arr.addLong(2);
            arr.addLong(3);
            Cursor obj = objectValue.setObject();
            obj.setLong("a", 1);
            obj.setLong("b", 2);
            obj.setLong("c", 3);
        }
    }

    private static class DestinationFixture {
        public final Slime slime1 = new Slime();
        public final Slime slime2 = new Slime();
        public final Slime slime3 = new Slime();
        public final Slime slime4 = new Slime();
        public final Slime slime5 = new Slime();
        public final Slime slime6 = new Slime();
        public final Slime slime7 = new Slime();
        public final Slime slime8 = new Slime();
        public final Slime slime9 = new Slime();
    }

    private final SourceFixture f1 = new SourceFixture();
    private final DestinationFixture f2 = new DestinationFixture();

    private final Injector injector = new Injector();

    private void inject(Inspector inspector, Inserter inserter) {
        injector.inject(inspector, inserter);
    }

    private void assertEqualTo(Slime left, Slime right) {
        assertTrue("'" + left + "' not equal to '" + right + "'", left.equalTo(right));
    }

    private void assertEqualTo(Inspector left, Inspector right) {
        assertTrue("'" + left + "' not equal to '" + right + "'", left.equalTo(right));
    }

    @Test
    public void injectIntoSlime() {
        assertTrue(f1.empty.get().valid()); // explicit nix

        inject(f1.empty.get(), new SlimeInserter(f2.slime1));
        inject(f1.nixValue.get(), new SlimeInserter(f2.slime2));
        inject(f1.boolValue.get(), new SlimeInserter(f2.slime3));
        inject(f1.longValue.get(), new SlimeInserter(f2.slime4));
        inject(f1.doubleValue.get(), new SlimeInserter(f2.slime5));
        inject(f1.stringValue.get(), new SlimeInserter(f2.slime6));
        inject(f1.dataValue.get(), new SlimeInserter(f2.slime7));
        inject(f1.arrayValue.get(), new SlimeInserter(f2.slime8));
        inject(f1.objectValue.get(), new SlimeInserter(f2.slime9));

        assertEquals(f1.empty.get().toString(), f2.slime1.get().toString());
        assertEquals(f1.nixValue.get().toString(), f2.slime2.get().toString());
        assertEquals(f1.boolValue.get().toString(), f2.slime3.get().toString());
        assertEquals(f1.longValue.get().toString(), f2.slime4.get().toString());
        assertEquals(f1.doubleValue.get().toString(), f2.slime5.get().toString());
        assertEquals(f1.stringValue.get().toString(), f2.slime6.get().toString());
        assertEquals(f1.dataValue.get().toString(), f2.slime7.get().toString());
        assertEquals(f1.arrayValue.get().toString(), f2.slime8.get().toString());
        assertEqualTo(f1.objectValue.get(), f2.slime9.get());

    }

    @Test
    public void injectIntoArray() {
        f2.slime1.setArray();
        inject(f1.empty.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.nixValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.boolValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.longValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.doubleValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.stringValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.dataValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.arrayValue.get(), new ArrayInserter(f2.slime1.get()));
        inject(f1.objectValue.get(), new ArrayInserter(f2.slime1.get()));

        assertEquals(f1.empty.get().toString(), f2.slime1.get().entry(0).toString());
        assertEquals(f1.nixValue.get().toString(), f2.slime1.get().entry(1).toString());
        assertEquals(f1.boolValue.get().toString(), f2.slime1.get().entry(2).toString());
        assertEquals(f1.longValue.get().toString(), f2.slime1.get().entry(3).toString());
        assertEquals(f1.doubleValue.get().toString(), f2.slime1.get().entry(4).toString());
        assertEquals(f1.stringValue.get().toString(), f2.slime1.get().entry(5).toString());
        assertEquals(f1.dataValue.get().toString(), f2.slime1.get().entry(6).toString());
        assertEquals(f1.arrayValue.get().toString(), f2.slime1.get().entry(7).toString());
        assertEqualTo(f1.objectValue.get(), f2.slime1.get().entry(8));
    }

    @Test
    public void injectIntoObject() {
        f2.slime1.setObject();
        inject(f1.empty.get(), new ObjectInserter(f2.slime1.get(), "a"));
        inject(f1.nixValue.get(), new ObjectInserter(f2.slime1.get(), "b"));
        inject(f1.boolValue.get(), new ObjectInserter(f2.slime1.get(), "c"));
        inject(f1.longValue.get(), new ObjectInserter(f2.slime1.get(), "d"));
        inject(f1.doubleValue.get(), new ObjectInserter(f2.slime1.get(), "e"));
        inject(f1.stringValue.get(), new ObjectInserter(f2.slime1.get(), "f"));
        inject(f1.dataValue.get(), new ObjectInserter(f2.slime1.get(), "g"));
        inject(f1.arrayValue.get(), new ObjectInserter(f2.slime1.get(), "h"));
        inject(f1.objectValue.get(), new ObjectInserter(f2.slime1.get(), "i"));

        assertEquals(f1.empty.get().toString(), f2.slime1.get().field("a").toString());
        assertEquals(f1.nixValue.get().toString(), f2.slime1.get().field("b").toString());
        assertEquals(f1.boolValue.get().toString(), f2.slime1.get().field("c").toString());
        assertEquals(f1.longValue.get().toString(), f2.slime1.get().field("d").toString());
        assertEquals(f1.doubleValue.get().toString(), f2.slime1.get().field("e").toString());
        assertEquals(f1.stringValue.get().toString(), f2.slime1.get().field("f").toString());
        assertEquals(f1.dataValue.get().toString(), f2.slime1.get().field("g").toString());
        assertEquals(f1.arrayValue.get().toString(), f2.slime1.get().field("h").toString());
        assertEqualTo(f1.objectValue.get(), f2.slime1.get().field("i"));
    }

    @Test
    public void invalidInjectionIsIgnored() {
        inject(f1.arrayValue.get(), new SlimeInserter(f2.slime1));
        assertEquals(3, f2.slime1.get().entries());
        inject(f1.longValue.get(), new ArrayInserter(f2.slime1.get()));
        assertEquals(4, f2.slime1.get().entries());
        inject(f1.doubleValue.get(), new ArrayInserter(f2.slime1.get()));
        assertEquals(5, f2.slime1.get().entries());
        inject(f1.nixValue.get().field("bogus"), new ArrayInserter(f2.slime1.get()));
        assertEquals(5, f2.slime1.get().entries());
    }

    @Test
    public void recursiveArrayInject() {
        Slime expect = new Slime();
        {
            Cursor arr = expect.setArray();
            arr.addLong(1);
            arr.addLong(2);
            arr.addLong(3);
            {
                Cursor arrCpy = arr.addArray();
                arrCpy.addLong(1);
                arrCpy.addLong(2);
                arrCpy.addLong(3);
            }
        }
        Slime data = new Slime();
        {
            Cursor arr = data.setArray();
            arr.addLong(1);
            arr.addLong(2);
            arr.addLong(3);
        }
        inject(data.get(), new ArrayInserter(data.get()));
        assertEquals(expect.toString(), data.toString());
    }

    @Test
    public void recursiveObjectInject() {
        Slime expect = new Slime();
        {
            Cursor obj = expect.setObject();
            obj.setLong("a", 1);
            obj.setLong("b", 2);
            obj.setLong("c", 3);
            {
                Cursor obj_cpy = obj.setObject("d");
                obj_cpy.setLong("a", 1);
                obj_cpy.setLong("b", 2);
                obj_cpy.setLong("c", 3);
            }
        }
        Slime data = new Slime();
        {
            Cursor obj = data.setObject();
            obj.setLong("a", 1);
            obj.setLong("b", 2);
            obj.setLong("c", 3);
        }
        inject(data.get(), new ObjectInserter(data.get(), "d"));
        assertEqualTo(expect, data);
    }
}