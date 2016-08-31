package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceScheduler;

import java.io.IOException;

/**
 * @author valerijf
 */
public class MaintenanceSchedulerMock implements MaintenanceScheduler {
    private static StringBuilder requests;

    private static final Object monitor = new Object();

    static {
        reset();
    }

    public MaintenanceSchedulerMock() {
        if (OrchestratorMock.semaphore.tryAcquire()) {
            throw new RuntimeException("OrchestratorMock.semaphore must be acquired before using MaintenanceSchedulerMock");
        }
    }

    @Override
    public void removeOldFilesFromNode(ContainerName containerName) {

    }

    @Override
    public void cleanNodeAdmin() {

    }

    @Override
    public void deleteContainerStorage(ContainerName containerName) throws IOException {
        synchronized (monitor) {
            requests.append("DeleteContainerStorage with ContainerName: ").append(containerName).append("\n");
        }
    }


    public static String getRequests() {
        return requests.toString();
    }

    public static void reset() {
        synchronized (monitor) {
            requests = new StringBuilder();
        }
    }
}
