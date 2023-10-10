// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This test is currently incomplete. Also much tested in the prelude module though.
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class XMLWriterTestCase {

    private XMLWriter xml;

    @Before
    public void setUp() {
        xml = new XMLWriter(new StringWriter());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test3Levels() {
        xml.openTag("l1").openTag("l2").openTag("l3").closeTag().closeTag().closeTag();
        assertEquals(
                "<l1>\n" +
                "\n" +
                "  <l2>\n" +
                "    <l3/>\n" +
                "  </l2>\n" +
                "\n" +
                "</l1>\n"
                , getWritten());
    }

    private String getWritten() {
        xml.close();
        return xml.getWrapped().toString();
    }

    @Test
    public void test3LevelsCustomFormatting() {
        xml=new XMLWriter(new StringWriter(),1,-1);
        xml.openTag("l1").openTag("l2").openTag("l3").closeTag().closeTag().closeTag();
        assertEquals(
                "<l1>\n" +
                "  <l2>\n" +
                "  <l3/>\n" +
                "  </l2>\n" +
                "</l1>\n"
                , getWritten());
    }

    @Test
    public void test4LevelsA() {
        xml.openTag("l1");
        xml.openTag("l21").closeTag();
        xml.openTag("l22");
        xml.openTag("l31").openTag("l4").closeTag().closeTag();
        xml.openTag("l32").closeTag();
        xml.closeTag();
        xml.closeTag();
        assertEquals(
                "<l1>\n" +
                "\n" +
                "  <l21/>\n" +
                "\n" +
                "  <l22>\n" +
                "    <l31>\n" +
                "      <l4/>\n" +
                "    </l31>\n" +
                "    <l32/>\n" +
                "  </l22>\n" +
                "\n" +
                "</l1>\n"
                , getWritten());
    }

    @Test
    public void test4LevelsB() {
        xml.openTag("l1");
        xml.openTag("l21");
        xml.openTag("l31").closeTag();
        xml.openTag("l32").openTag("l4").closeTag().closeTag();
        xml.closeTag();
        xml.openTag("l22").closeTag();
        xml.closeTag();
        assertEquals(
                "<l1>\n" +
                "\n" +
                "  <l21>\n" +
                "    <l31/>\n" +
                "    <l32>\n" +
                "      <l4/>\n" +
                "    </l32>\n" +
                "  </l21>\n" +
                "\n" +
                "  <l22/>\n" +
                "\n" +
                "</l1>\n"
                , getWritten());
    }

    @Test
    public void testEmpty() {
        xml.openTag("l1").closeTag();
        assertEquals(
                "<l1/>\n"
                , getWritten());
    }

    @Test
    public void checkHeader() {
        xml.xmlHeader("utf-8");
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n", getWritten());
    }

    @Test
    public void forcedAttribute() {
        xml.openTag("a").forceAttribute(new Utf8String("nalle"), "\"").closeTag();
        assertEquals("<a nalle=\"&quot;\"/>\n", getWritten());
    }

    @Test
    public void attributeString() {
        xml.openTag("a").attribute(new Utf8String("nalle"), new Utf8String("b")).closeTag();
        assertEquals("<a nalle=\"b\"/>\n", getWritten());
    }

    @Test
    public void attributeLong() {
        xml.openTag("a").attribute(new Utf8String("nalle"), 5L).closeTag();
        assertEquals("<a nalle=\"5\"/>\n", getWritten());
    }

    @Test
    public void attributeBoolean() {
        xml.openTag("a").attribute(new Utf8String("nalle"), true).closeTag();
        assertEquals("<a nalle=\"true\"/>\n", getWritten());
    }

    @Test
    public void content() {
        xml.content("a\na", false).content("a\na", true);
        assertEquals("a\naa\na", getWritten());
    }

    @Test
    public void escapedContent() {
        xml.escapedContent("a&\na", false).escapedContent("a&\na", true);
        assertEquals("a&\naa&\na", getWritten());
    }

    @Test
    public void escapedAsciiContent() {
        xml.escapedAsciiContent("a&\na", false).escapedAsciiContent("a&\na", true);
        assertEquals("a&\naa&\na", getWritten());
    }

    @Test
    public void isIn() {
        assertFalse(xml.isIn("a"));
        xml.openTag("a");
        assertTrue(xml.isIn("a"));
    }

}
