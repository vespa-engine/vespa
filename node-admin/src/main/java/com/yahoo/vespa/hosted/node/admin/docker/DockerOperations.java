// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.Optional;

public interface DockerOperations {
    Optional<String> getVespaVersion(ContainerName containerName);

    // Returns true if container is absent on return
    boolean removeContainerIfNeeded(ContainerNodeSpec nodeSpec, String hostname, Orchestrator orchestrator)
            throws Exception;

    // Returns true if started
    boolean startContainerIfNeeded(ContainerNodeSpec nodeSpec);

    // Returns false if image is already downloaded
    boolean shouldScheduleDownloadOfImage(DockerImage dockerImage);

    void scheduleDownloadOfImage(ContainerNodeSpec nodeSpec, Runnable callback);

    void executeCommand(ContainerName containerName, String[] command);

    void executeResume(ContainerName containerName);

    Docker.ContainerStats getContainerStats(ContainerName containerName);
}
