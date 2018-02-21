// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author freva
 */
public class PathResolver {
    public static final Path ROOT = Paths.get("/");
    public static final Path RELATIVE_APPLICATION_STORAGE_PATH = Paths.get("home/docker/container-storage");

    private final Path vespaHomePathForContainer;
    private final Path applicationStoragePathForNodeAdmin;
    private final Path applicationStoragePathForHost;

    public PathResolver() {
        this(
                Paths.get(Defaults.getDefaults().vespaHome()),
                Paths.get("/host").resolve(RELATIVE_APPLICATION_STORAGE_PATH),
                ROOT.resolve(RELATIVE_APPLICATION_STORAGE_PATH));
    }

    public PathResolver(Path vespaHomePathForContainer, Path applicationStoragePathForNodeAdmin, Path applicationStoragePathForHost) {
        this.vespaHomePathForContainer = vespaHomePathForContainer;
        this.applicationStoragePathForNodeAdmin = applicationStoragePathForNodeAdmin;
        this.applicationStoragePathForHost = applicationStoragePathForHost;
    }

    public Path getVespaHomePathForContainer() {
        return vespaHomePathForContainer;
    }

    public Path getApplicationStoragePathForNodeAdmin() {
        return applicationStoragePathForNodeAdmin;
    }

    public Path getApplicationStoragePathForHost() {
        return applicationStoragePathForHost;
    }
}
