// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class CapacityTest {

    @Test
    void testCapacityValidation() {
        // Equal min and max is allowed
        Capacity.from(new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)),
                      new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)),
                      IntRange.empty(),
                      false,
                      true,
                      Optional.empty(),
                      ClusterInfo.empty());
        assertValidationFailure(new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)),
                               new ClusterResources(2, 2, new NodeResources(1, 2, 3, 4)));
        assertValidationFailure(new ClusterResources(4, 4, new NodeResources(1, 2, 3, 4)),
                               new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)));
        assertValidationFailure(new ClusterResources(4, 2, new NodeResources(2, 2, 3, 4)),
                               new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)));
        assertValidationFailure(new ClusterResources(4, 2, new NodeResources(1, 3, 3, 4)),
                               new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)));
        assertValidationFailure(new ClusterResources(4, 2, new NodeResources(1, 2, 4, 4)),
                               new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)));
        assertValidationFailure(new ClusterResources(4, 2, new NodeResources(1, 2, 3, 5)),
                               new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)));
        // It's enough that one dimension is smaller also when the others are larger
        assertValidationFailure(new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)),
                               new ClusterResources(8, 4, new NodeResources(2, 1, 6, 8)));

        assertEquals("Cannot set hostTTL without a custom cloud account",
                     assertThrows(IllegalArgumentException.class,
                                  () -> Capacity.from(new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)),
                                                      new ClusterResources(4, 2, new NodeResources(1, 2, 3, 4)),
                                                      IntRange.empty(), false, true, Optional.empty(), new ClusterInfo.Builder().hostTTL(Duration.ofSeconds(1)).build()))
                             .getMessage());
    }

    private void assertValidationFailure(ClusterResources min, ClusterResources max) {
        assertEquals("The max capacity must be larger than the min capacity, but got min " + min + " and max " + max,
                     assertThrows(IllegalArgumentException.class,
                                  () -> Capacity.from(min, max, IntRange.empty(), false, true, Optional.empty(), ClusterInfo.empty()))
                             .getMessage());
    }

}
