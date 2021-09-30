// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class ContainerNameTest {
    @Test
    public void testAlphanumericalContainerName() {
        String name = "container123";
        ContainerName containerName = new ContainerName(name);
        assertEquals(containerName.asString(), name);
    }

    @Test
    public void testAlphanumericalWithDashContainerName() {
        String name = "container-123";
        ContainerName containerName = new ContainerName(name);
        assertEquals(containerName.asString(), name);
    }

    @Test
    public void testContainerNameFromHostname() {
        assertEquals(new ContainerName("container-123"), ContainerName.fromHostname("container-123.sub.domain.tld"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testAlphanumericalWithSlashContainerName() {
        new ContainerName("container/123");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyContainerName() {
        new ContainerName("");
    }

    @Test(expected=NullPointerException.class)
    public void testNullContainerName() {
        new ContainerName(null);
    }
}
