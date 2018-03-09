// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.component;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class PathResolverTest {
    @Test
    public void testNodeAdminOnHost() {
        PathResolver pathResolver = new PathResolver(Paths.get("/"), Paths.get("/home/y"));
        assertEquals(Paths.get("/home/docker/container-storage"), pathResolver.getApplicationStoragePathForHost());
        assertEquals(Paths.get("/home/docker/container-storage"), pathResolver.getApplicationStoragePathForNodeAdmin());
        assertEquals(Paths.get("/"), pathResolver.getPathToRootOfHost());
        assertEquals(Paths.get("/home/y"), pathResolver.getVespaHomePathForContainer());
    }

    @Test
    public void testNodeAdminInContainer() {
        PathResolver pathResolver = new PathResolver(Paths.get("/host"), Paths.get("/home/y"));
        assertEquals(Paths.get("/home/docker/container-storage"), pathResolver.getApplicationStoragePathForHost());
        assertEquals(Paths.get("/host/home/docker/container-storage"), pathResolver.getApplicationStoragePathForNodeAdmin());
        assertEquals(Paths.get("/host"), pathResolver.getPathToRootOfHost());
        assertEquals(Paths.get("/home/y"), pathResolver.getVespaHomePathForContainer());
    }
}