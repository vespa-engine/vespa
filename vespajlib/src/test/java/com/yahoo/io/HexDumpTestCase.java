// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import org.junit.Test;

import com.yahoo.text.Utf8;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HexDumpTestCase {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset UTF16 = Charset.forName("UTF-16");

    @Test
    public void requireThatToHexStringAcceptsNull() {
        assertNull(HexDump.toHexString(null));
    }

    @Test
    public void requireThatToHexStringIsUnformatted() {
        assertEquals("6162636465666768696A6B6C6D6E6F707172737475767778797A",
                     HexDump.toHexString("abcdefghijklmnopqrstuvwxyz".getBytes(UTF8)));
        assertEquals("FEFF006100620063006400650066006700680069006A006B006C00" +
                     "6D006E006F0070007100720073007400750076007700780079007A",
                     HexDump.toHexString("abcdefghijklmnopqrstuvwxyz".getBytes(UTF16)));
    }

}
