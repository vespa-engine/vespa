// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;

import java.time.Clock;
import java.util.Optional;

import static org.mockito.Mockito.mock;

/**
 * @author freva
 */
public class StorageMaintainerMock extends StorageMaintainer {
    private final CallOrderVerifier callOrderVerifier;

    public StorageMaintainerMock(DockerOperations dockerOperations, ProcessExecuter processExecuter, Environment environment, CallOrderVerifier callOrderVerifier, Clock clock) {
        super(dockerOperations, processExecuter, environment, mock(CoredumpHandler.class), clock);
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public Optional<Long> getDiskUsageFor(ContainerName containerName) {
        return Optional.empty();
    }

    @Override
    public void handleCoreDumpsForContainer(ContainerName containerName, NodeSpec node) {
    }

    @Override
    public void removeOldFilesFromNode(ContainerName containerName) {
    }

    @Override
    public void cleanNodeAdmin() {
    }

    @Override
    public void cleanupNodeStorage(ContainerName containerName, NodeSpec node) {
        callOrderVerifier.add("DeleteContainerStorage with " + containerName);
    }
}
