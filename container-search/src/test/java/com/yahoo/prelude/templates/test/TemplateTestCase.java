// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates.test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import com.yahoo.io.ByteWriter;
import com.yahoo.prelude.templates.UserTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Steinar Knutsen
 */
public class TemplateTestCase {

    private CharsetEncoder encoder;
    private ByteArrayOutputStream stream;

    public TemplateTestCase () {
        Charset cs = Charset.forName("UTF-8");
        encoder = cs.newEncoder();
        stream = new ByteArrayOutputStream();
    }

    @Test
    public void testASCIIQuoting() throws java.io.IOException {
        stream.reset();
        byte[] c = new byte[] { 97, 98, 99, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        UserTemplate.dumpAndXMLQuoteUTF8(bw, c);
        bw.close();
        String res = stream.toString("UTF-8");
        String correct = "abc\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\u0008\t\n\\u000B\\u000C\r\\u000E\\u000F\\u0010\\u0011";
        assertEquals(correct, res);

    }

    @Test
    public void testXMLQuoting() throws java.io.IOException {
        stream.reset();
        // c = <s>&gt;
        byte[] c = new byte[] { 60, 115, 62, 38, 103, 116, 59 };
        ByteWriter bw = new ByteWriter(stream, encoder);
        UserTemplate.dumpAndXMLQuoteUTF8(bw, c);
        bw.close();
        String res = stream.toString("UTF-8");
        String correct = "&lt;s&gt;&amp;gt;";
        assertEquals(correct, res);

    }

}
