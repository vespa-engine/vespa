// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BufferedOutputTest {

    @Test
    public void putOfAnArrayKeepsEveryByteWhenTheBufferGrows() {
        BufferedOutput out = new BufferedOutput(64); // the smallest capacity
        byte[] expected = new byte[66];
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) (i * 7);

        out.put(Arrays.copyOfRange(expected, 0, 63));
        out.put(new byte[0]);
        out.put(Arrays.copyOfRange(expected, 63, 65)); // crosses the capacity of 64
        out.put(expected[65]);

        assertEquals(66, out.position());
        assertArrayEquals(expected, out.toArray());

        out.reset();
        out.put(new byte[] { 1, 2 });
        assertArrayEquals(new byte[] { 1, 2 }, out.toArray());
    }

}
