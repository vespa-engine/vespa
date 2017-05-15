// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.util.Optional;

/**
 * @author freva
 */
public class StorageMaintainerMock extends StorageMaintainer {
    private final CallOrderVerifier callOrderVerifier;

    public StorageMaintainerMock(Docker docker, Environment environment, CallOrderVerifier callOrderVerifier) {
        super(docker, new MetricReceiverWrapper(MetricReceiver.nullImplementation), environment);
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public Optional<Long> updateIfNeededAndGetDiskMetricsFor(ContainerName containerName) {
        return Optional.empty();
    }

    @Override
    public void handleCoreDumpsForContainer(ContainerName containerName, ContainerNodeSpec nodeSpec, Environment environment) {
    }

    @Override
    public void removeOldFilesFromNode(ContainerName containerName) {
    }

    @Override
    public void cleanNodeAdmin() {
    }

    @Override
    public void archiveNodeData(ContainerName containerName) {
        callOrderVerifier.add("DeleteContainerStorage with " + containerName);
    }
}
