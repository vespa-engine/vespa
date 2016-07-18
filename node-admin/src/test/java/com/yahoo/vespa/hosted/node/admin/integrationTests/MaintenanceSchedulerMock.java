package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceScheduler;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.io.IOException;
import java.util.Arrays;

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
    public void addRegularJob(String... args) {
        synchronized (monitor) {
            requests.append("addRegularJob with ").append(Arrays.toString(args)).append("\n");
        }
    }

    @Override
    public void runMaintenanceJob(String... args) {
        synchronized (monitor) {
            requests.append("runMaintenanceJob with ").append(Arrays.toString(args)).append("\n");
        }
    }

    @Override
    public void deleteOldAppData(String path, long maxAge) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_APP_DATA, "--path=" + path, "--max_age=" + maxAge);
    }

    @Override
    public void deleteOldAppData(String path, long maxAge, String name) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_APP_DATA, "--path=" + path, "--max_age=" + maxAge, "--name=" + name);
    }

    @Override
    public void deleteOldLogs(String path, long maxAge) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_LOGS, "--path=" + path, "--max_age=" + maxAge);
    }

    @Override
    public void deleteOldLogs(String path, long maxAge, String name) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_LOGS, "--path=" + path, "--max_age=" + maxAge, "--name=" + name);
    }

    @Override
    public void cleanNodeAgent() {

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
