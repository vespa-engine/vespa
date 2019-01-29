// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Before;
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

    @Before
    public void setUp() {
    }

    private void inject(Inspector inspector, Inserter inserter) {
        injector.inject(inspector, inserter);
    }

    private Inserter slimeInserter(Slime slime) {
        var inserter = new SlimeInserter();
        inserter.adjust(slime);
        return inserter;
    }

    private Inserter arrayInserter(Cursor cursor) {
        var inserter = new ArrayInserter();
        inserter.adjust(cursor);
        return inserter;
    }

    private Inserter objectInserter(Cursor cursor, String name) {
        var inserter = new ObjectInserter();
        inserter.adjust(cursor, name);
        return inserter;
    }

    private void assertEqualTo(Inspector left, Inspector right) {
        assertTrue("'" + left + "' not equal to '" + right + "'", left.equalTo(right));
    }

    @Test
    public void injectIntoSlime() {
        assertTrue(f1.empty.get().valid()); // explicit nix

        inject(f1.empty.get(), slimeInserter(f2.slime1));
        inject(f1.nixValue.get(), slimeInserter(f2.slime2));
        inject(f1.boolValue.get(), slimeInserter(f2.slime3));
        inject(f1.longValue.get(), slimeInserter(f2.slime4));
        inject(f1.doubleValue.get(), slimeInserter(f2.slime5));
        inject(f1.stringValue.get(), slimeInserter(f2.slime6));
        inject(f1.dataValue.get(), slimeInserter(f2.slime7));
        inject(f1.arrayValue.get(), slimeInserter(f2.slime8));
        inject(f1.objectValue.get(), slimeInserter(f2.slime9));

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
        inject(f1.empty.get(), arrayInserter(f2.slime1.get()));
        inject(f1.nixValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.boolValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.longValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.doubleValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.stringValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.dataValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.arrayValue.get(), arrayInserter(f2.slime1.get()));
        inject(f1.objectValue.get(), arrayInserter(f2.slime1.get()));

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
        inject(f1.empty.get(), objectInserter(f2.slime1.get(), "a"));
        inject(f1.nixValue.get(), objectInserter(f2.slime1.get(), "b"));
        inject(f1.boolValue.get(), objectInserter(f2.slime1.get(), "c"));
        inject(f1.longValue.get(), objectInserter(f2.slime1.get(), "d"));
        inject(f1.doubleValue.get(), objectInserter(f2.slime1.get(), "e"));
        inject(f1.stringValue.get(), objectInserter(f2.slime1.get(), "f"));
        inject(f1.dataValue.get(), objectInserter(f2.slime1.get(), "g"));
        inject(f1.arrayValue.get(), objectInserter(f2.slime1.get(), "h"));
        inject(f1.objectValue.get(), objectInserter(f2.slime1.get(), "i"));

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
        inject(f1.arrayValue.get(), slimeInserter(f2.slime1));
        assertEquals(3, f2.slime1.get().entries());
        inject(f1.longValue.get(), arrayInserter(f2.slime1.get()));
        assertEquals(4, f2.slime1.get().entries());
        inject(f1.doubleValue.get(), arrayInserter(f2.slime1.get()));
        assertEquals(5, f2.slime1.get().entries());
        inject(f1.nixValue.get().field("bogus"), arrayInserter(f2.slime1.get()));
        assertEquals(5, f2.slime1.get().entries());
    }
}