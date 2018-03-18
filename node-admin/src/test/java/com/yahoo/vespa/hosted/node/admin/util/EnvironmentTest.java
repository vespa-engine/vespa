// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class EnvironmentTest {
    private final Environment environment = new Environment.Builder()
            .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
            .region("us-east-1")
            .environment("prod")
            .system("main")
            .pathResolver(new PathResolver())
            .defaultFlavor("d-2-8-50")
            .cloud("mycloud")
            .build();

    @Test
    public void testPathInNodeToPathInNodeAdminAndHost() {
        ContainerName containerName = new ContainerName("docker1-1");
        assertEquals(
                "/host/home/docker/container-storage/" + containerName.asString(),
                environment.pathInNodeAdminFromPathInNode(containerName, Paths.get("/")).toString());

        assertEquals(
                "/home/docker/container-storage/" + containerName.asString(),
                environment.pathInHostFromPathInNode(containerName, Paths.get("/")).toString());
    }

    @Test
    public void testAbsolutePathInNodeConversion() {
        String varPath = getDefaults().underVespaHome("var");
        ContainerName containerName = new ContainerName("docker1-1");
        String expected = "/host/home/docker/container-storage/" + containerName.asString() + varPath;
        String[] absolutePathsInContainer = {"/" + varPath, varPath, varPath + "/"};

        for (String pathInContainer : absolutePathsInContainer) {
            assertEquals(expected, environment.pathInNodeAdminFromPathInNode(containerName, Paths.get(pathInContainer)).toString());
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNonAbsolutePathInNodeConversion() {
        Path varPath = Paths.get("some/relative/path");
        environment.pathInNodeAdminFromPathInNode(new ContainerName("container-1"), varPath);
    }
}
