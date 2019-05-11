// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
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
        b.addFlavor("host-large", 6., 6., 6, Flavor.Type.BARE_METAL);
        b.addFlavor("host-small", 3., 3., 3, Flavor.Type.BARE_METAL);
        b.addFlavor("d-1", 1, 1., 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3", 3, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-disk", 3, 3., 5, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-mem", 3, 5., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-cpu", 5, 3., 3, Flavor.Type.DOCKER_CONTAINER);

        FlavorsConfig flavors = b.build();
        Flavor hostLargeFlavor = new Flavor(flavors.flavor(0));
        Flavor hostSmallFlavor = new Flavor(flavors.flavor(1));
        Flavor d1Flavor = new Flavor(flavors.flavor(2));
        Flavor d2Flavor = new Flavor(flavors.flavor(3));
        Flavor d3Flavor = new Flavor(flavors.flavor(4));
        Flavor d3DiskFlavor = new Flavor(flavors.flavor(5));
        Flavor d3MemFlavor = new Flavor(flavors.flavor(6));
        Flavor d3CPUFlavor = new Flavor(flavors.flavor(7));

        ResourceCapacity capacityOfHostSmall = ResourceCapacity.of(hostSmallFlavor);

        // Assert initial capacities
        assertTrue(capacityOfHostSmall.hasCapacityFor(hostSmallFlavor));
        assertTrue(capacityOfHostSmall.hasCapacityFor(d1Flavor));
        assertTrue(capacityOfHostSmall.hasCapacityFor(d2Flavor));
        assertTrue(capacityOfHostSmall.hasCapacityFor(d3Flavor));
        assertFalse(capacityOfHostSmall.hasCapacityFor(hostLargeFlavor));

        // Also check that we are taking all three resources into accout
        assertFalse(capacityOfHostSmall.hasCapacityFor(d3DiskFlavor));
        assertFalse(capacityOfHostSmall.hasCapacityFor(d3MemFlavor));
        assertFalse(capacityOfHostSmall.hasCapacityFor(d3CPUFlavor));

        // Compare it to various flavors
        assertEquals(1, compare(capacityOfHostSmall, nodeCapacity(d1Flavor)));
        assertEquals(1, compare(capacityOfHostSmall, nodeCapacity(d2Flavor)));
        assertEquals(0, compare(capacityOfHostSmall, nodeCapacity(d3Flavor)));
        assertEquals(-1, compare(capacityOfHostSmall, nodeCapacity(d3DiskFlavor)));
        assertEquals(-1, compare(capacityOfHostSmall, nodeCapacity(d3CPUFlavor)));
        assertEquals(-1, compare(capacityOfHostSmall, nodeCapacity(d3MemFlavor)));

        // Change free capacity and assert on rest capacity
        capacityOfHostSmall = capacityOfHostSmall.subtract(ResourceCapacity.of(d1Flavor));
        assertEquals(0, compare(capacityOfHostSmall, nodeCapacity(d2Flavor)));

        // Assert on rest capacity
        assertTrue(capacityOfHostSmall.hasCapacityFor(d1Flavor));
        assertFalse(capacityOfHostSmall.hasCapacityFor(d3Flavor));

        // At last compare the disk and cpu and mem variations
        assertEquals(-1, compare(nodeCapacity(d3Flavor), nodeCapacity(d3DiskFlavor)));
        assertEquals(1, compare(nodeCapacity(d3DiskFlavor), nodeCapacity(d3CPUFlavor)));
        assertEquals(-1, compare(nodeCapacity(d3CPUFlavor), nodeCapacity(d3MemFlavor)));
        assertEquals(1, compare(nodeCapacity(d3MemFlavor), nodeCapacity(d3DiskFlavor)));
    }

    private ResourceCapacity nodeCapacity(Flavor flavor) {
        return ResourceCapacity.of(flavor);
    }

    private int compare(ResourceCapacity a, ResourceCapacity b) {
        return ResourceCapacityComparator.defaultOrder().compare(a, b);
    }

}
