// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

public interface DockerOperationsInterface {
    String getVespaVersionOrNull(ContainerName containerName);

    // Returns true if started
    boolean startContainerIfNeeded(ContainerNodeSpec nodeSpec);

    // Returns true if scheduling download
    boolean shouldScheduleDownloadOfImage(DockerImage dockerImage);

    boolean removeContainerIfNeeded(ContainerNodeSpec nodeSpec, HostName hostname, Orchestrator orchestrator)
            throws Exception;

    void startContainer(ContainerNodeSpec nodeSpec);

    void executeResume(ContainerName containerName);
}
