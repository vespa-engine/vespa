// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class NodeIncidesTest {

    @Test
    public void testNonCompactIndices() {
        NodeIndices indices = new NodeIndices(List.of(1, 3, 4), false);
        assertEquals(5, indices.probeNext());
        assertEquals(6, indices.probeNext());

        indices.resetProbe();
        assertEquals(5, indices.probeNext());
        assertEquals(6, indices.probeNext());

        indices.commitProbe();
        assertEquals(7, indices.probeNext());
        assertEquals(8, indices.probeNext());

        indices.resetProbe();
        assertEquals(7, indices.next());
        assertEquals(8, indices.next());

        assertEquals(9, indices.probeNext());
        try {
            indices.next();
        }
        catch (IllegalStateException e) {
            assertEquals("Must commit ongoing probe before calling 'next'", e.getMessage());
        }
    }


    @Test
    public void testCompactIndices() {
        NodeIndices indices = new NodeIndices(List.of(1, 3, 4), true);
        assertEquals(0, indices.probeNext());
        assertEquals(2, indices.probeNext());
        assertEquals(5, indices.probeNext());
        assertEquals(6, indices.probeNext());

        indices.resetProbe();
        assertEquals(0, indices.probeNext());
        assertEquals(2, indices.probeNext());

        indices.commitProbe();
        assertEquals(5, indices.probeNext());
        assertEquals(6, indices.probeNext());

        indices.resetProbe();
        assertEquals(5, indices.next());
        assertEquals(6, indices.next());

        assertEquals(7, indices.probeNext());
        try {
            indices.next();
        }
        catch (IllegalStateException e) {
            assertEquals("Must commit ongoing probe before calling 'next'", e.getMessage());
        }
    }

}
