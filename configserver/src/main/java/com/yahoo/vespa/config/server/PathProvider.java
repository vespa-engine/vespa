// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.path.Path;

/**
 * Temporary provider of root path for components that will soon get them injected from a parent class.
 *
 * @author lulf
 * * @since 5.1.24
 */
public class PathProvider {

    private final Path root;
    // Path for Vespa-related data stored in Zookeeper (subpaths are relative to this path)
    // NOTE: This should not be exposed, as this path can be different in testing, depending on how we configure it.
    private static final String APPS_ZK_NODE               = "sessions";
    private static final String VESPA_ZK_PATH              = "/vespa/config";

    @Inject
    public PathProvider() {
        root = Path.fromString(VESPA_ZK_PATH);
    }

    public PathProvider(Path root) {
        this.root = root;
    }

    public Path getRoot() {
        return root;
    }

    public Path getSessionDirs() {
        return root.append(APPS_ZK_NODE);
    }

    public Path getSessionDir(long sessionId) {
        return getSessionDirs().append(String.valueOf(sessionId));
    }

}
