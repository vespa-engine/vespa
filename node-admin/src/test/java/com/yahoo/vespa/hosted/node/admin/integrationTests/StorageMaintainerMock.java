// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;

import java.io.IOException;

/**
 * @author valerijf
 */
public class StorageMaintainerMock extends StorageMaintainer {
    private final CallOrderVerifier callOrder;

    public StorageMaintainerMock(CallOrderVerifier callOrder) {
        this.callOrder = callOrder;
    }

    @Override
    public void updateDockerUsage(String hostname, ContainerName containerName, Docker.ContainerStats stats) {
    }

    @Override
    public void removeOldFilesFromNode(ContainerName containerName) {
    }

    @Override
    public void cleanNodeAdmin() {
    }

    @Override
    public void deleteContainerStorage(ContainerName containerName) throws IOException {
        callOrder.add("DeleteContainerStorage with ContainerName: " + containerName);
    }
}
