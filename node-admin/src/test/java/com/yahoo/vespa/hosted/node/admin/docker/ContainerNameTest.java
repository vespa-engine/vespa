package com.yahoo.vespa.hosted.node.admin.docker;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author valerijf
 */
public class ContainerNameTest {
    @Test
    public void testAlphanumericalContainerName() {
        String name = "container123";
        ContainerName containerName = new ContainerName(name);
        assertThat(containerName.asString(), is(name));
    }

    @Test
    public void testAlphanumericalWithDashContainerName() {
        String name = "container-123";
        ContainerName containerName = new ContainerName(name);
        assertThat(containerName.asString(), is(name));
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
