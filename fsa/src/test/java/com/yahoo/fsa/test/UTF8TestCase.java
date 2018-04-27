// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.test;

import com.yahoo.fsa.FSA;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author geirst
 */
public class UTF8TestCase {

    private Charset charset = Charset.forName("utf-8");
    private FSA fsa;
    private FSA.State state;
    private byte prefixBuf[];
    private byte suffixBuf[];
    private String prefix;
    private String suffix;
    private String word;

    private static byte [] convert(int [] buf) {
        byte retval[] = new byte[buf.length];
        for (int i = 0; i < buf.length; ++i) {
            retval[i] = (byte)buf[i];
        }
        return retval;
    }

    @Before
    public void setUp() {
        fsa = new FSA("src/test/fsa/utf8.fsa"); // fsa with one word (6 code points, 18 bytes)
        state = fsa.getState();
        int pbuf[] = {0xe0,0xa4,0xb9};
        prefixBuf = convert(pbuf);
        prefix = new String(prefixBuf, charset);
        int sbuf[] = {0xe0,0xa4,0xbf,0xe0,0xa4,0xa8,0xe0,0xa5,0x8d,0xe0,0xa4,0xa6,0xe0,0xa5,0x80};
        suffixBuf = convert(sbuf);
        suffix = new String(suffixBuf, charset);
        word = prefix + suffix;
    }

    @Test
    public void testStringDelta() {
        state.delta(word);
        assertTrue(state.isFinal());
    }

    @Test
    public void testCharDelta() {
        assertEquals(6, word.length());
        for (int i = 0; i < word.length(); ++i) {
            state.delta(word.charAt(i));
            assertTrue(state.isValid());
        }
        assertTrue(state.isFinal());
    }

    @Test
    public void testByteDelta() {
        FSA.State state = fsa.getState();
        assertEquals(3, prefixBuf.length);
        for (int i = 0; i < prefixBuf.length; ++i) {
            state.delta(prefixBuf[i]);
            assertTrue(state.isValid());
        }
        assertEquals(15, suffixBuf.length);
        for (int i = 0; i < suffixBuf.length; ++i) {
            state.delta(suffixBuf[i]);
            assertTrue(state.isValid());
        }
        assertTrue(state.isFinal());
    }

    @Test
    public void testIteratorAtStart() {
        Iterator<FSA.Iterator.Item> itr = fsa.iterator(state);
        FSA.Iterator.Item item = itr.next();
        assertEquals(word, item.getString());
        assertFalse(itr.hasNext());
    }

    @Test
    public void testIteratorWithPrefix() {
        state.delta(prefix);
        Iterator<FSA.Iterator.Item> itr = fsa.iterator(state);
        FSA.Iterator.Item item = itr.next();
        assertEquals(suffix, item.getString());
        assertFalse(itr.hasNext());
    }

    @Test
    public void testIteratorWithCompleteWord() {
        state.delta(word);
        Iterator<FSA.Iterator.Item> itr = fsa.iterator(state);
        FSA.Iterator.Item item = itr.next();
        assertEquals("", item.getString());
        assertFalse(itr.hasNext());
    }

}
