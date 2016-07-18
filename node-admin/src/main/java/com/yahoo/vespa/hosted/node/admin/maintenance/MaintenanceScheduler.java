package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;

import java.io.IOException;

/**
 * @author valerijf
 */
public interface MaintenanceScheduler {
    /**
     * Adds a maintenance job to regular queue. These jobs are run once every {@link MaintenanceSchedulerImpl#rate}.
     * @param args Job name and other optional additional arguments for the maintenance script
     */
    void addRegularJob(String... args);

    /**
     * Executes a single maintenance job. This call is blocking.
     * @param args Job name and other optional additional arguments for the maintenance script
     */
    void runMaintenanceJob(String... args);

    void deleteOldAppData(String path, long maxAge);

    void deleteOldAppData(String path, long maxAge, String name);

    void deleteOldLogs(String path, long maxAge);

    void deleteOldLogs(String path, long maxAge, String name);

    void cleanNodeAgent();

    void cleanNodeAdmin();

    void deleteContainerStorage(ContainerName containerName) throws IOException;
}
