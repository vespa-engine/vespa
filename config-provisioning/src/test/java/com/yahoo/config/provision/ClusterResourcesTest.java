// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ClusterResourcesTest {

    @Test
    public void testCost() {
        ClusterResources r1 = new ClusterResources(3, 1, new NodeResources(2,  8, 50, 1));
        ClusterResources r2 = new ClusterResources(3, 1, new NodeResources(2, 16, 50, 1));
        assertEquals(1.818, r1.cost() + r2.cost(), 0.01);
    }

}
