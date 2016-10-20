// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author valerijf
 */
public class StorageMaintainerMock extends StorageMaintainer {
    private final CallOrderVerifier callOrderVerifier;

    public StorageMaintainerMock(CallOrderVerifier callOrderVerifier) {
        super(new Maintainer());
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public Map<String, Number> updateIfNeededAndGetDiskMetricsFor(ContainerName containerName) {
        return new HashMap<>();
    }

    @Override
    public void removeOldFilesFromNode(ContainerName containerName) {
    }

    @Override
    public void cleanNodeAdmin() {
    }

    @Override
    public void archiveNodeData(ContainerName containerName) throws IOException {
        callOrderVerifier.add("DeleteContainerStorage with ContainerName: " + containerName);
    }
}
