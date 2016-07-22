package com.yahoo.vespa.hosted.node.maintenance;

import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author valerijf
 */
public class MaintainerTest {
    @Test
    public void testPathInNodeToPathInNodeAdminAndHost() {
        ContainerName containerName = new ContainerName("docker1-1");
        assertEquals(
                "/host/home/docker/container-storage/" + containerName.asString(),
                Maintainer.pathInNodeAdminFromPathInNode(containerName, "/").toString());

        assertEquals(
                "/home/docker/container-storage/" + containerName.asString(),
                Maintainer.pathInHostFromPathInNode(containerName, "/").toString());
    }

    @Test
    public void testAbsolutePathInNodeConversion() {
        ContainerName containerName = new ContainerName("docker1-1");
        String expected = "/host/home/docker/container-storage/" + containerName.asString() + "/home/y/var";
        String[] absolutePathsInContainer = {"//home/y/var", "/home/y/var", "/home/y/var/"};

        for (String pathInContainer : absolutePathsInContainer) {
            assertEquals(expected, Maintainer.pathInNodeAdminFromPathInNode(containerName, pathInContainer).toString());
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNonAbsolutePathInNodeConversion() {
        Maintainer.pathInNodeAdminFromPathInNode(new ContainerName("container-1"), "home/y/var");
    }
}
