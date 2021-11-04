// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerFileSystem;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author freva
 */
public class PathScope {

    private final ContainerFileSystem containerFs;
    private final String pathToVespaHome;
    private final UserScope users;

    public PathScope(ContainerFileSystem containerFs, String pathToVespaHome) {
        this.containerFs = Objects.requireNonNull(containerFs);
        this.pathToVespaHome = Objects.requireNonNull(pathToVespaHome);
        this.users = containerFs.getUserPrincipalLookupService().userScope();
    }

    public ContainerPath of(String pathInNode) {
        return of(pathInNode, users.root());
    }

    public ContainerPath of(String pathInNode, UnixUser user) {
        return ContainerPath.fromPathInContainer(containerFs, Path.of(pathInNode), user);
    }

    public ContainerPath underVespaHome(String relativePath) {
        if (relativePath.startsWith("/"))
            throw new IllegalArgumentException("Expected a relative path to the Vespa home, got: " + relativePath);

        return ContainerPath.fromPathInContainer(containerFs, Path.of(pathToVespaHome, relativePath), users.vespa());
    }

    public ContainerPath fromPathOnHost(Path pathOnHost) {
        return ContainerPath.fromPathOnHost(containerFs, pathOnHost, users.root());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathScope pathScope = (PathScope) o;
        return containerFs.equals(pathScope.containerFs) && pathToVespaHome.equals(pathScope.pathToVespaHome) && users.equals(pathScope.users);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerFs, pathToVespaHome, users);
    }
}
