// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Bjorn Borud
 */
public class HTMLTestCase {

    @Test
    public void testSimpleEscape() {
        assertEquals("&quot;this &lt;&amp;&gt; that&quot;",
                HTML.htmlescape("\"this <&> that\""));
    }

    @Test
    public void testBunchOfEscapes() {
        assertEquals(
                "&copy;&reg;&Agrave;&Aacute;&Acirc;&Atilde;&Auml;&Aring;&AElig;&Ccedil;",
                HTML.htmlescape("\u00A9\u00AE\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u00C7"));

        assertEquals(
                "&Egrave;&Eacute;&Ecirc;&Euml;&Igrave;&Iacute;&Icirc;&Iuml;&ETH;&Ntilde;",
                HTML.htmlescape("\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF\u00D0\u00D1"));

        assertEquals(
                "&Ograve;&Oacute;&Ocirc;&Otilde;&Ouml;&Oslash;&Ugrave;&Uacute;&Ucirc;&Uuml;",
                HTML.htmlescape("\u00D2\u00D3\u00D4\u00D5\u00D6\u00D8\u00D9\u00DA\u00DB\u00DC"));

        assertEquals(
                "&Yacute;&THORN;&szlig;&agrave;&aacute;&acirc;&atilde;&auml;&aring;&aelig;",
                HTML.htmlescape("\u00DD\u00DE\u00DF\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6"));

        assertEquals(
                "&ccedil;&egrave;&eacute;&ecirc;&euml;&igrave;&iacute;&icirc;&iuml;&igrave;",
                HTML.htmlescape("\u00E7\u00E8\u00E9\u00EA\u00EB\u00EC\u00ED\u00EE\u00EF\u00EC"));

        assertEquals(
                "&iacute;&icirc;&iuml;&eth;&ntilde;&ograve;&oacute;&ocirc;&otilde;&ouml;",
                HTML.htmlescape("\u00ED\u00EE\u00EF\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F6"));

        assertEquals(
                "&oslash;&ugrave;&uacute;&ucirc;&uuml;&yacute;&thorn;&yuml;",
                HTML.htmlescape("\u00F8\u00F9\u00FA\u00FB\u00FC\u00FD\u00FE\u00FF"));
    }

}
