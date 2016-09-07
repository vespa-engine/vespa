package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceScheduler;

import java.io.IOException;

/**
 * @author valerijf
 */
public class MaintenanceSchedulerMock implements MaintenanceScheduler {
    private final CallOrderVerifier callOrder;

    public MaintenanceSchedulerMock(CallOrderVerifier callOrder) {
        this.callOrder = callOrder;
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
