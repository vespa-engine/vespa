// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author valerijf
 */
public class MaintainerTest {
    @Test
    public void testPathInNodeToPathInNodeAdminAndHost() {
        ContainerName containerName = new ContainerName("docker1-1");
        Maintainer maintainer = new Maintainer();
        assertEquals(
                "/host/home/docker/container-storage/" + containerName.asString(),
                maintainer.pathInNodeAdminFromPathInNode(containerName, "/").toString());

        assertEquals(
                "/home/docker/container-storage/" + containerName.asString(),
                maintainer.pathInHostFromPathInNode(containerName, "/").toString());
    }

    @Test
    public void testAbsolutePathInNodeConversion() {
        ContainerName containerName = new ContainerName("docker1-1");
        String expected = "/host/home/docker/container-storage/" + containerName.asString() + "/home/y/var";
        String[] absolutePathsInContainer = {"//home/y/var", "/home/y/var", "/home/y/var/"};

        for (String pathInContainer : absolutePathsInContainer) {
            assertEquals(expected, new Maintainer().pathInNodeAdminFromPathInNode(containerName, pathInContainer).toString());
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNonAbsolutePathInNodeConversion() {
        new Maintainer().pathInNodeAdminFromPathInNode(new ContainerName("container-1"), "home/y/var");
    }
}
