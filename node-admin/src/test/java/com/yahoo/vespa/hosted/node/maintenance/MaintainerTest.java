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
}
