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
    public static final Path DEFAULT_HOST_ROOT = Paths.get("/host");
    public static final Path RELATIVE_APPLICATION_STORAGE_PATH = Paths.get("home/docker/container-storage");

    private final Path hostRoot;
    private final Path vespaHomePathForContainer;

    private final Path applicationStoragePathForNodeAdmin;
    private final Path applicationStoragePathForHost;

    /**
     * @param hostRoot              the absolute path to the root of the host's file system
     * @param vespaHomeForContainer the absolute path of Vespa home in the mount namespace of any
     *                              and all Docker containers managed by Node Admin.
     */
    public PathResolver(Path hostRoot, Path vespaHomeForContainer) {
        if (!hostRoot.isAbsolute()) {
            throw new IllegalArgumentException("Path to root of host file system is not absolute: " +
                    hostRoot);
        }
        this.hostRoot = hostRoot;

        if (!vespaHomeForContainer.isAbsolute()) {
            throw new IllegalArgumentException("Path to Vespa home is not absolute: " + vespaHomeForContainer);
        }
        this.vespaHomePathForContainer = vespaHomeForContainer;

        this.applicationStoragePathForNodeAdmin = hostRoot.resolve(RELATIVE_APPLICATION_STORAGE_PATH);
        this.applicationStoragePathForHost = ROOT.resolve(RELATIVE_APPLICATION_STORAGE_PATH);
    }

    public PathResolver() {
        this(DEFAULT_HOST_ROOT, Paths.get(Defaults.getDefaults().vespaHome()));
    }

    /** For testing */
    public PathResolver(Path vespaHomePathForContainer, Path applicationStoragePathForNodeAdmin, Path applicationStoragePathForHost) {
        this.hostRoot = DEFAULT_HOST_ROOT;
        this.vespaHomePathForContainer = vespaHomePathForContainer;
        this.applicationStoragePathForNodeAdmin = applicationStoragePathForNodeAdmin;
        this.applicationStoragePathForHost = applicationStoragePathForHost;
    }

    /**
     * Returns the absolute path of the Vespa home directory in any Docker container mount namespace.
     *
     * It's a limitation of current implementation that all containers MUST have the same Vespa
     * home directory path.
     */
    public Path getVespaHomePathForContainer() {
        return vespaHomePathForContainer;
    }

    /** Returns the absolute path to the container storage directory for the node admin (this process). */
    public Path getApplicationStoragePathForNodeAdmin() {
        return applicationStoragePathForNodeAdmin;
    }

    /** Returns the absolute path to the container storage directory for the host. */
    public Path getApplicationStoragePathForHost() {
        return applicationStoragePathForHost;
    }

    /**
     * Returns the absolute path to the directory which is the root directory of the host
     * file system.
     */
    public Path getPathToRootOfHost() {
        return hostRoot;
    }
}
