// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class XMLTestCase  {

    @Test
    public void testSimple() {
        String s1 = "this is a < test";
        String s2 = "this is a & test";
        String s3 = "this is a \" test";
        String s4 = "this is a <\" test";
        String s5 = "this is a low \u001F test";

        assertEquals("this is a &lt; test", XML.xmlEscape(s1, true));
        assertEquals("this is a &amp; test", XML.xmlEscape(s2, true));

        // quotes are only escaped in attributes
        //
        assertEquals("this is a &quot; test", XML.xmlEscape(s3, true));
        assertEquals("this is a \" test", XML.xmlEscape(s3, false));

        // quotes are only escaped in attributes.  prevent bug
        // no. 187006 from happening again!
        //
        assertEquals("this is a &lt;&quot; test", XML.xmlEscape(s4, true));
        assertEquals("this is a &lt;\" test", XML.xmlEscape(s4, false));

        assertEquals("this is a low \uFFFD test", XML.xmlEscape(s5, false));
        String s = XML.xmlEscape(s5, false, false);
        assertEquals(0x1F, s.toCharArray()[14]);
    }

    @Test
    public void testInvalidUnicode() {
        assertEquals("a\ufffd\ufffdb",XML.xmlEscape("a\uffff\uffffb", false));
    }

    @Test
    public void testInvalidUnicodeAlongWithEscaping() {
        assertEquals("a\ufffd\ufffdb&amp;",XML.xmlEscape("a\ufffe\uffffb&", false));
    }

    @Test
    public void testWhenFirstCharacterMustBeEscaped() {
        assertEquals("&amp;co", XML.xmlEscape("&co", false));
        assertEquals("\ufffd is a perfectly fine character;",
                XML.xmlEscape("\u0000 is a perfectly fine character;", false));
    }

    @Test
    public void testLineNoise() {
        assertEquals("\ufffda\ufffd\ufffd\ufffdb&amp;\u380c\ufb06\uD87E\uDDF2\ufffd  \ufffd",
                XML.xmlEscape("\u0001a\u0000\ufffe\uffffb&\u380c\ufb06\uD87E\uDDF2\uD87E  \uD87E", false));
    }

    @Test
    public void testZeroLength() {
        assertEquals("", XML.xmlEscape("", false));
    }

    @Test
    public void testAllEscaped() {
        assertEquals("&amp;\ufffd\ufffd", XML.xmlEscape("&\u0000\uffff", false));
    }

    @Test
    public void testNoneEscaped() {
        assertEquals("a\ud87e\uddf2\u00e5", XML.xmlEscape("a\ud87e\uddf2\u00e5", false));
    }

    @Test
    public void testReturnSameIfNoQuoting() {
        String a = "abc";
        String b = XML.xmlEscape(a, false);
        assertSame("xmlEscape should return its input if no change is necessary.",
                a, b);
    }

    @Test
    public void testValidAttributeNames() {
        assertTrue(XML.isName(":A_a\u00C0\u00D8\u00F8\u0370\u037F\u200C\u2070\u2C00\u3001\uF900\uFDF0\uD800\uDC00"));
        assertFalse(XML.isName(" "));
        assertFalse(XML.isName(": "));
        assertTrue(XML.isName("sss"));
    }

    @Test
    public void testExceptionContainingLineNumberAndColumnNumber() {
        final String invalidXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<foo";
        try {
            XML.getDocument(new StringReader(invalidXml));
            fail("Did not get expected exception");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().contains("Could not parse '"));
            assertTrue(e.getMessage().contains("error at line 2, column 5"));
        }
    }
}
