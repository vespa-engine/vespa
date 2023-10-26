// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provisioning.FlavorsConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author smorgrav
 */
public class ResourceCapacityTest {

    @Test
    public void basic_capacity_and_compare_operations() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("host-large", 6., 6., 6, 6, Flavor.Type.BARE_METAL);
        b.addFlavor("host-small", 3., 3., 3, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("d-1", 1, 1., 1, 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, 2, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3", 3, 3., 3, 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-disk", 3, 3., 5, 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-mem", 3, 5., 3, 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-cpu", 5, 3., 3, 3, Flavor.Type.DOCKER_CONTAINER);

        FlavorsConfig flavors = b.build();
        Flavor hostLargeFlavor = new Flavor(flavors.flavor(0));
        Flavor hostSmallFlavor = new Flavor(flavors.flavor(1));
        Flavor d1Flavor = new Flavor(flavors.flavor(2));
        Flavor d2Flavor = new Flavor(flavors.flavor(3));
        Flavor d3Flavor = new Flavor(flavors.flavor(4));
        Flavor d3DiskFlavor = new Flavor(flavors.flavor(5));
        Flavor d3MemFlavor = new Flavor(flavors.flavor(6));
        Flavor d3CPUFlavor = new Flavor(flavors.flavor(7));

        NodeResources capacityOfHostSmall = hostSmallFlavor.resources();

        // Assert initial capacities
        assertTrue(capacityOfHostSmall.satisfies(hostSmallFlavor.resources()));
        assertTrue(capacityOfHostSmall.satisfies(d1Flavor.resources()));
        assertTrue(capacityOfHostSmall.satisfies(d2Flavor.resources()));
        assertTrue(capacityOfHostSmall.satisfies(d3Flavor.resources()));
        assertFalse(capacityOfHostSmall.satisfies(hostLargeFlavor.resources()));

        // Also check that we are taking all three resources into accout
        assertFalse(capacityOfHostSmall.satisfies(d3DiskFlavor.resources()));
        assertFalse(capacityOfHostSmall.satisfies(d3MemFlavor.resources()));
        assertFalse(capacityOfHostSmall.satisfies(d3CPUFlavor.resources()));

        // Compare it to various flavors
        assertEquals(1, compare(capacityOfHostSmall, d1Flavor.resources()));
        assertEquals(1, compare(capacityOfHostSmall, d2Flavor.resources()));
        assertEquals(0, compare(capacityOfHostSmall, d3Flavor.resources()));
        assertEquals(-1, compare(capacityOfHostSmall, d3DiskFlavor.resources()));
        assertEquals(-1, compare(capacityOfHostSmall, d3CPUFlavor.resources()));
        assertEquals(-1, compare(capacityOfHostSmall, d3MemFlavor.resources()));

        // Change free capacity and assert on rest capacity
        capacityOfHostSmall = capacityOfHostSmall.subtract(d1Flavor.resources());
        assertEquals(0, compare(capacityOfHostSmall, d2Flavor.resources()));

        // Assert on rest capacity
        assertTrue(capacityOfHostSmall.satisfies(d1Flavor.resources()));
        assertFalse(capacityOfHostSmall.satisfies(d3Flavor.resources()));

        // At last compare the disk and cpu and mem variations
        assertEquals(-1, compare(d3Flavor.resources(), d3DiskFlavor.resources()));
        assertEquals(1, compare(d3DiskFlavor.resources(), d3CPUFlavor.resources()));
        assertEquals(-1, compare(d3CPUFlavor.resources(), d3MemFlavor.resources()));
        assertEquals(1, compare(d3MemFlavor.resources(), d3DiskFlavor.resources()));

        assertEquals(-1, compare(new NodeResources(1, 2, 3, 1, NodeResources.DiskSpeed.slow),
                                          new NodeResources(1, 2, 3, 1, NodeResources.DiskSpeed.fast)));
        assertEquals(1, compare(new NodeResources(1, 2, 3, 1, NodeResources.DiskSpeed.fast),
                                         new NodeResources(1, 2, 3, 1, NodeResources.DiskSpeed.slow)));
    }

    private int compare(NodeResources a, NodeResources b) {
        return NodeResourceComparator.defaultOrder().compare(a, b);
    }

}
