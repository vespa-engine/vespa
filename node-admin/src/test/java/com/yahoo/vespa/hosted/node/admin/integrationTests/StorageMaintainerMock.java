// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;

import java.util.Optional;

/**
 * @author freva
 */
public class StorageMaintainerMock extends StorageMaintainer {
    private final CallOrderVerifier callOrderVerifier;

    public StorageMaintainerMock(DockerOperations dockerOperations, CallOrderVerifier callOrderVerifier) {
        super(dockerOperations, null, null);
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public Optional<Long> getDiskUsageFor(NodeAgentContext context) {
        return Optional.empty();
    }

    @Override
    public void handleCoreDumpsForContainer(NodeAgentContext context, NodeSpec node, Optional<Container> container) {
    }

    @Override
    public void removeOldFilesFromNode(NodeAgentContext context) {
    }

    @Override
    public void archiveNodeStorage(NodeAgentContext context) {
        callOrderVerifier.add("DeleteContainerStorage with " + context.containerName());
    }
}
