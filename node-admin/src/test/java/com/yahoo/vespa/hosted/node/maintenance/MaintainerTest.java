package com.yahoo.vespa.hosted.node.maintenance;

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
                Maintainer.applicationStoragePathForNode("docker1-1").toString());
    }
}
