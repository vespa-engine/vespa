// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 * @author Steinar Knutsen
 */
public class HexDumpTestCase {

    @Test
    public void requireThatToHexStringAcceptsNull() {
        assertNull(HexDump.toHexString(null));
    }

    @Test
    public void requireThatToHexStringIsUnformatted() {
        assertEquals("6162636465666768696A6B6C6D6E6F707172737475767778797A",
                     HexDump.toHexString("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)));
        assertEquals("FEFF006100620063006400650066006700680069006A006B006C00" +
                     "6D006E006F0070007100720073007400750076007700780079007A",
                     HexDump.toHexString("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_16)));
    }

}
