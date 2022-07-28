// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author freva
 */
public class ContainerResourcesTest {

    @Test
    void verify_unlimited() {
        assertEquals(-1, ContainerResources.UNLIMITED.cpuQuota());
        assertEquals(100_000, ContainerResources.UNLIMITED.cpuPeriod());
        assertEquals(0, ContainerResources.UNLIMITED.cpuShares());
    }

    @Test
    void validate_shares() {
        new ContainerResources(0, 0, 0);
        new ContainerResources(0, 2, 0);
        new ContainerResources(0, 2048, 0);
        new ContainerResources(0, 262_144, 0);

        assertThrows(IllegalArgumentException.class, () -> new ContainerResources(0, -1, 0)); // Negative shares not allowed
        assertThrows(IllegalArgumentException.class, () -> new ContainerResources(0, 1, 0)); // 1 share not allowed
        assertThrows(IllegalArgumentException.class, () -> new ContainerResources(0, 262_145, 0));
    }

    @Test
    void cpu_shares_scaling() {
        ContainerResources resources = ContainerResources.from(5.3, 2.5, 0);
        assertEquals(530_000, resources.cpuQuota());
        assertEquals(100_000, resources.cpuPeriod());
        assertEquals(80, resources.cpuShares());
    }

    private static void assertThrows(Class<? extends Throwable> clazz, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected " + clazz);
        } catch (Throwable e) {
            if (!clazz.isInstance(e)) throw e;
        }
    }
}
