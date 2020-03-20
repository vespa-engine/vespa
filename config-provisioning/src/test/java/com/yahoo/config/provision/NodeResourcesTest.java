// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeResourcesTest {

    @Test
    public void testToString() {
        assertEquals("[vcpu: 1.0, memory: 10.0 Gb, disk 100.0 Gb]",
                     new NodeResources(1., 10., 100., 0).toString());
        assertEquals("[vcpu: 0.3, memory: 3.3 Gb, disk 33.3 Gb, bandwidth: 0.30 Gbps]",
                     new NodeResources(1/3., 10/3., 100/3., 0.3).toString());
    }

}
