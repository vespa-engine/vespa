// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.test;

import com.yahoo.processing.request.CompoundName;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * @author  bratseth
 */
public class CompoundNameTestCase {

    @Test
    public void testFirstRest() {
        assertEquals(CompoundName.empty, CompoundName.empty.rest());

        CompoundName n=new CompoundName("on.two.three");
        assertEquals("on", n.first());
        assertEquals("two.three", n.rest().toString());
        n=n.rest();
        assertEquals("two", n.first());
        assertEquals("three", n.rest().toString());
        n=n.rest();
        assertEquals("three", n.first());
        assertEquals("", n.rest().toString());
        n=n.rest();
        assertEquals("", n.first());
        assertEquals("", n.rest().toString());
        n=n.rest();
        assertEquals("", n.first());
        assertEquals("", n.rest().toString());
    }

    @Test
    public void testHashCodeAndEquals() {
        CompoundName n1 = new CompoundName("venn.d.a");
        CompoundName n2 = new CompoundName(n1.asList());
        assertEquals(n1.hashCode(), n2.hashCode());
        assertEquals(n1, n2);
    }

    @Test
    public void testAppend() {
        assertEquals("a",new CompoundName("a").append("").toString());
        assertEquals("a",new CompoundName("").append("a").toString());
        assertEquals("a.b",new CompoundName("a").append("b").toString());

        CompoundName name = new CompoundName("a.b");
        assertEquals("a.b.c",name.append("c").toString());
        assertEquals("a.b.d",name.append("d").toString());
    }

    @Test
    public void testEmpty() {
        CompoundName empty=new CompoundName("");
        assertEquals("", empty.toString());
        assertEquals(0, empty.asList().size());
    }

    @Test
    public void testAsList() {
        assertEquals("[one]", new CompoundName("one").asList().toString());
        assertEquals("[one, two, three]", new CompoundName("one.two.three").asList().toString());
    }

}
