// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerNetworkMode;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

public interface NodeAgentContext extends TaskContext {

    /** @return node specification from node-repository */
    NodeSpec node();

    /** @return node ACL from node-repository */
    Acl acl();

    /** @return name of the docker container this context applies to */
    ContainerName containerName();

    /** @return hostname of the docker container this context applies to */
    default HostName hostname() {
        return HostName.from(node().hostname());
    }

    default NodeType nodeType() {
        return node().type();
    }

    AthenzIdentity identity();

    ContainerNetworkMode networkMode();

    ZoneApi zone();

    String vespaUser();

    String vespaUserOnHost();

    default boolean isDisabled(NodeAgentTask task) {
        return false;
    };

    /**
     * The vcpu value in NodeSpec is the number of vcpus required by the node on a fixed historical
     * baseline machine.  However the current host has a faster per-vcpu performance by a scale factor
     * (see flavors.def cpuSpeedup), and therefore do not need to set aside the full number of vcpus
     * to run the node.  This method returns that reduced number of vcpus.
     *
     * @return the vcpus required by the node on this host.
     */
    double vcpuOnThisHost();

    /** The file system used by the NodeAgentContext. All paths must have the same provider. */
    FileSystem fileSystem();

    /**
     * This method is the inverse of {@link #pathInNodeFromPathOnHost(Path)}}
     *
     * @param pathInNode absolute path in the container
     * @return the absolute path on host pointing at the same inode
     */
    Path pathOnHostFromPathInNode(Path pathInNode);

    default Path pathOnHostFromPathInNode(String pathInNode) {
        return pathOnHostFromPathInNode(fileSystem().getPath(pathInNode));
    }

    /**
     * This method is the inverse of {@link #pathOnHostFromPathInNode(Path)}
     *
     * @param pathOnHost absolute path on host
     * @return the absolute path in the container pointing at the same inode
     */
    Path pathInNodeFromPathOnHost(Path pathOnHost);

    default Path pathInNodeFromPathOnHost(String pathOnHost) {
        return pathInNodeFromPathOnHost(fileSystem().getPath(pathOnHost));
    }

    /**
     * @param relativePath relative path under Vespa home in container
     * @return the absolute path under Vespa home in the container
     */
    Path pathInNodeUnderVespaHome(Path relativePath);

    default Path pathInNodeUnderVespaHome(String relativePath) {
        return pathInNodeUnderVespaHome(fileSystem().getPath(relativePath));
    }

    Optional<ApplicationId> hostExclusiveTo();
}
