package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;

import java.io.IOException;

/**
 * @author valerijf
 */
public interface MaintenanceScheduler {
    void removeOldFilesFromNode(ContainerName containerName);

    void cleanNodeAdmin();

    void deleteContainerStorage(ContainerName containerName) throws IOException;
}
