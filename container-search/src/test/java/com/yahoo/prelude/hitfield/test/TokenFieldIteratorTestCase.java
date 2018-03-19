// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield.test;

import java.util.ListIterator;

import com.yahoo.prelude.hitfield.FieldPart;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.StringFieldPart;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the FieldTokenIterator class
 *
 * @author Steinar Knutsen
 */
public class TokenFieldIteratorTestCase {

    @Test
    public void testTokenIteratorNext() {
        HitField hf = new HitField("boo", "hei paa deg");
        assertEquals(3, hf.getTokenizedContent().size());
        ListIterator<?> l = hf.tokenIterator();
        FieldPart p = (FieldPart)l.next();
        assertEquals("hei", p.getContent());
        p = (FieldPart)l.next();
        assertEquals("paa", p.getContent());
        p = (FieldPart)l.next();
        assertEquals("deg", p.getContent());
        assertEquals(false, l.hasNext());
    }

    @Test
    public void testTokenIteratorPrevious() {
        HitField hf = new HitField("boo", "hei paa");
        ListIterator<?> l = hf.tokenIterator();
        FieldPart p = (FieldPart)l.next();
        assertEquals("hei", p.getContent());
        p = (FieldPart)l.next();
        assertEquals("paa", p.getContent());
        p = (FieldPart)l.previous();
        assertEquals("paa", p.getContent());
        p = (FieldPart)l.previous();
        assertEquals("hei", p.getContent());
    }

    @Test
    public void testTokenIteratorSet() {
        HitField hf = new HitField("boo", "hei paa deg");
        assertEquals(3, hf.getTokenizedContent().size());
        ListIterator<FieldPart> l = hf.tokenIterator();
        l.next();
        l.next();
        l.set(new StringFieldPart("aap", true));
        l.next();
        assertEquals(false, l.hasNext());
        l.previous();
        l.set(new StringFieldPart("ged", true));
        assertEquals("hei aap ged", hf.getContent());
    }

    @Test
    public void testTokenIteratorAdd() {
        HitField hf = new HitField("boo", "hei paa deg");
        assertEquals(3, hf.getTokenizedContent().size());
        ListIterator<FieldPart> l = hf.tokenIterator();
        l.add(new StringFieldPart("a", true));
        l.next();
        l.next();
        l.add(new StringFieldPart("b", true));
        l.next();
        l.add(new StringFieldPart("c", true));
        assertEquals(false, l.hasNext());
        assertEquals("ahei paab degc", hf.getContent());
    }

    @Test
    public void testTokenIteratorRemove() {
        HitField hf = new HitField("boo", "hei paa deg");
        ListIterator<FieldPart> l = hf.tokenIterator();
        l.next();
        l.next();
        l.remove();
        l.add(new StringFieldPart("hallo", true));
        assertEquals(3, hf.getTokenizedContent().size());
        assertEquals("hei hallo deg", hf.getContent());
        l.next();
        l.previous();
        l.previous();
        l.remove();
        assertEquals("hei  deg", hf.getContent());
        l.add(new StringFieldPart("paa", true));
        assertEquals("hei paa deg", hf.getContent());
    }

}
