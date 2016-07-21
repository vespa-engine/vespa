package com.yahoo.vespa.hosted.node.maintenance;

import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author valerijf
 */
public class MaintainerTest {
    @Test
    public void locationOfContainerStorageInNodeAdmin() {
        assertEquals(
                "/host/home/docker/container-storage/docker1-1",
                Maintainer.applicationStoragePathForNode(new ContainerName("docker1-1")).toString());
    }

    @Test
    public void testPathRelativeToContainer() {
        ContainerName containerName = new ContainerName("docker1-1");
        String expected = "/host/home/docker/container-storage/" + containerName.asString() + "/home/y/var";
        String[] variations = {"//home/y/var", "/home/y/var", "home/y/var", "/home/y/var/"};

        for (String variation : variations) {
            assertEquals(expected, Maintainer.applicationStoragePathRelativeToNode(containerName, variation).toString());
        }
    }
}
