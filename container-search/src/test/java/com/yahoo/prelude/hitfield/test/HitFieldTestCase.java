// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield.test;

import java.util.ArrayList;
import java.util.List;

import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.StringFieldPart;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the HitField class
 *
 * @author Lars Chr Jensen
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class HitFieldTestCase {

    @Test
    public void testHitField() {
        HitField hf = new HitField("boo", "hei paa deg");
        assertEquals(3, hf.getTokenizedContent().size());
        List l = new ArrayList();
        l.add(new StringFieldPart("foo", true));
        l.add(new StringFieldPart(" ", false));
        l.add(new StringFieldPart("bar", true));
        hf.setTokenizedContent(l);
        assertEquals("foo bar", hf.getContent());
        assertEquals("hei paa deg", hf.getRawContent());
    }

    @Test
    public void testCjk() {
        HitField hf = new HitField("boo", "hmm\u001fgr");
        assertEquals(2, hf.getTokenizedContent().size());
        assertEquals("hmmgr", hf.getContent());
        List l = new ArrayList();
        l.add(new StringFieldPart("foo", true));
        l.add(new StringFieldPart("bar", true));
        hf.setTokenizedContent(l);
        assertEquals("foobar", hf.getContent());
    }

    @Test
    public void testAnnotateField() {
        HitField hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9include\uFFFAincludes\uFFFB the <hi>Eclipse</hi> Platform");
        assertEquals(11, hf.getTokenizedContent().size());
        hf = new HitField("boo", "\uFFF9include\uFFFAincludes\uFFFB the <hi>Eclipse</hi> Platform");
        assertEquals(6, hf.getTokenizedContent().size());
        hf = new HitField("boo", "clude\uFFFAincludes\uFFFB the <hi>Eclipse</hi> Platform");
        assertEquals(5, hf.getTokenizedContent().size());
        hf = new HitField("boo", "\uFFFAincludes\uFFFB the <hi>Eclipse</hi> Platform");
        assertEquals(5, hf.getTokenizedContent().size());
        hf = new HitField("boo", "cludes\uFFFB the <hi>Eclipse</hi> Platform");
        assertEquals(5, hf.getTokenizedContent().size());
        hf = new HitField("boo", "\uFFFB the <hi>Eclipse</hi> Platform");
        assertEquals(5, hf.getTokenizedContent().size());
        hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9include\uFFFAincludes\uFFFB");
        assertEquals(6, hf.getTokenizedContent().size());
        hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9include\uFFFAincl");
        assertEquals(6, hf.getTokenizedContent().size());
        hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9include\uFFFA");
        assertEquals(6, hf.getTokenizedContent().size());
        hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9incl");
        assertEquals(6, hf.getTokenizedContent().size());
        hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9");
        assertEquals(6, hf.getTokenizedContent().size());
        hf = new HitField("boo", "The <hi>Eclipse</hi> SDK \uFFF9include\uFFFAincludes\uFFFB the <hi>Eclipse</hi> \uFFF9platform\uFFFAPlatforms\uFFFB test");
        assertEquals(12, hf.getTokenizedContent().size());
    }

    @Test
    public void testEmptyField() {
        HitField hf = new HitField("boo", "");
        assertEquals(0, hf.getTokenizedContent().size());
    }

}
