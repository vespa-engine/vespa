// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * @author stiankri
 */
public interface NodeRepository {
    List<ContainerNodeSpec> getContainersToRun() throws IOException;

    Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException;

    void updateNodeAttributes(
            HostName hostName,
            long restartGeneration,
            DockerImage dockerImage,
            String containerVespaVersion)
            throws IOException;

    void markAsReady(HostName hostName) throws IOException;
}
