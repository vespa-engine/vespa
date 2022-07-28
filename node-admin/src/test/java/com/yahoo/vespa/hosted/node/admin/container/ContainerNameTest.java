// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author freva
 */
public class ContainerNameTest {
    @Test
    void testAlphanumericalContainerName() {
        String name = "container123";
        ContainerName containerName = new ContainerName(name);
        assertEquals(containerName.asString(), name);
    }

    @Test
    void testAlphanumericalWithDashContainerName() {
        String name = "container-123";
        ContainerName containerName = new ContainerName(name);
        assertEquals(containerName.asString(), name);
    }

    @Test
    void testContainerNameFromHostname() {
        assertEquals(new ContainerName("container-123"), ContainerName.fromHostname("container-123.sub.domain.tld"));
    }

    @Test
    void testAlphanumericalWithSlashContainerName() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ContainerName("container/123");
        });
    }

    @Test
    void testEmptyContainerName() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ContainerName("");
        });
    }

    @Test
    void testNullContainerName() {
        assertThrows(NullPointerException.class, () -> {
            new ContainerName(null);
        });
    }
}
