// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.serialization.XmlStream;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for XmlStream used in document XML serialization.
 *
 * @author Håkon Humberset
 */
public class XmlStreamTestCase {

    /** A catch all test checking that regular usage looks good. */
    @Test
    public void testNormalUsage() {
        XmlStream xml = new XmlStream();
        xml.setIndent("  ");
        xml.beginTag("foo");
        xml.addAttribute("bar", "foo");
        xml.addAttribute("foo", "bar");
        xml.addContent("foo");
        xml.beginTag("bar");
        xml.endTag();
        xml.beginTag("foo");
        xml.beginTag("bar");
        xml.addAttribute("foo", "bar");
        xml.addContent("bar");
        xml.endTag();
        xml.endTag();
        xml.addContent("bar");
        xml.beginTag("foo");
        xml.addContent("foo");
        xml.addContent("bar");
        xml.endTag();
        xml.endTag();
        String expected =
              "<foo bar=\"foo\" foo=\"bar\">\n"
            + "  foo\n"
            + "  <bar/>\n"
            + "  <foo>\n"
            + "    <bar foo=\"bar\">bar</bar>\n"
            + "  </foo>\n"
            + "  bar\n"
            + "  <foo>foobar</foo>\n"
            + "</foo>\n";
        assertEquals(expected, xml.toString());
    }

    /**
     * Test that XML tag and attribute names are checked for validity.
     * Only the obvious illegal characters are tested currently.
     */
    @Test
    public void testIllegalAttributeNames() {
        String illegalNames[] = {">foo", "foo<bar", " foo", "bar ", "foo bar", "foo\"bar", "&foo"};
        XmlStream xml = new XmlStream();
        for (String name : illegalNames) {
            try {
                xml.beginTag(name);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().indexOf("cannot be used as an XML tag name") != -1);
            }
        }
        xml.beginTag("test");
        for (String name : illegalNames) {
            try {
                xml.addAttribute(name, "");
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().indexOf("cannot be used as an XML attribute name") != -1);
            }
        }
    }

    /** Test that XML attribute values are XML escaped. */
    @Test
    public void testAttributeEscaping() {
        //String badString = "\"&<>'\n\r\u0000";
        String badString = "\"&<>'";
        XmlStream xml = new XmlStream();
        xml.beginTag("foo");
        xml.addAttribute("bar", badString);
        xml.endTag();
        String expected = "<foo bar=\"&quot;&amp;&lt;&gt;'\"/>\n";
        assertEquals(expected, xml.toString());
    }

    /** Test that content is XML escaped. */
    @Test
    public void testContentEscaping() {
        //String badString = "\"&<>'\n\r\u0000";
        String badString = "\"&<>'";
        XmlStream xml = new XmlStream();
        xml.beginTag("foo");
        xml.addContent(badString);
        xml.endTag();
        String expected = "<foo>\"&amp;&lt;&gt;'</foo>\n";
        assertEquals(expected, xml.toString());
    }

}
