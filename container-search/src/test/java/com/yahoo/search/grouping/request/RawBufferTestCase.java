// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class RawBufferTestCase {

    // --------------------------------------------------------------------------------
    //
    // Tests.
    //
    // --------------------------------------------------------------------------------

    @Test
    void requireThatCompareWorks() {
        RawBuffer buffer = new RawBuffer();
        buffer.put((byte) 'a');
        buffer.put((byte) 'b');

        RawBuffer buffer2 = new RawBuffer();
        buffer2.put((byte) 'k');
        buffer2.put((byte) 'a');

        ArrayList<Byte> backing = new ArrayList<>();
        backing.add((byte) 'a');
        backing.add((byte) 'b');
        RawBuffer buffer3 = new RawBuffer(backing);

        assertEquals(buffer.compareTo(buffer2), -1);
        assertEquals(buffer2.compareTo(buffer), 1);
        assertEquals(buffer.compareTo(buffer3), 0);
    }

    @Test
    void requireThatToStringWorks() {
        assertToString(Arrays.asList("a".getBytes()[0], "b".getBytes()[0]), "{97,98}");
        assertToString(Arrays.asList((byte) 2, (byte) 6), "{2,6}");
    }

    public void assertToString(List<Byte> data, String expected) {
        RawBuffer buffer = new RawBuffer();
        for (Byte b : data) {
            buffer.put(b.byteValue());
        }
        assertEquals(buffer.toString(), expected);
    }
}
