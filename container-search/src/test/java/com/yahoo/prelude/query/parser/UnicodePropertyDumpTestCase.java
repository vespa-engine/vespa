// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Test;

/**
 * Test UnicodePropertyDump gives expected data.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class UnicodePropertyDumpTestCase {

    @Test
    public final void testMain() throws IOException {
        ByteArrayOutputStream toCheck;
        PrintStream out;
        toCheck = new ByteArrayOutputStream();
        out = new PrintStream(toCheck, false, "UTF-8");
        // 002E;FULL STOP;Po;0;CS;;;;;N;PERIOD;;;;
        UnicodePropertyDump.dumpProperties(0x2E, 0x2E + 1, true, out);
        // 00C5;LATIN CAPITAL LETTER A WITH RING ABOVE;Lu;0;L;0041 030A;;;;N;LATIN CAPITAL LETTER A RING;;;00E5;
        UnicodePropertyDump.dumpProperties(0xC5, 0xC5 + 1, true, out);
        // 1D7D3;MATHEMATICAL BOLD DIGIT FIVE;Nd;0;EN;<font> 0035;5;5;5;N;;;;;
        UnicodePropertyDump.dumpProperties(0x1D7D3, 0x1D7D3 + 1, true, out);
        out.flush();
        toCheck.flush();
        final String result = toCheck.toString("UTF-8");

        String expected = "0000002e 0000 24\n000000c5 0002 1\n0001d7d3 0006 5\n";
        assertEquals(expected, result);
    }
}
