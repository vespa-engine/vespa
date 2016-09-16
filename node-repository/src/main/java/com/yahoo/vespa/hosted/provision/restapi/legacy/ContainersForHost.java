// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

import java.util.List;

/**
 * Represents the JSON reply for getContainersForHost.
 * Serialized by jackson, and therefore uses public fields to avoid writing cruft.
 *
 * @author tonytv
 */
public class ContainersForHost {

    public List<DockerContainer> dockerContainers;

    public static class DockerContainer {
        public String containerHostname;
        public String dockerImage;
        public String nodeState;
        public long wantedRestartGeneration;
        public long currentRestartGeneration;

        public DockerContainer(
                String containerHostname,
                String dockerImage,
                String nodeState,
                long wantedRestartGeneration,
                long currentRestartGeneration) {
            this.containerHostname = containerHostname;
            this.dockerImage = dockerImage;
            this.nodeState = nodeState;
            this.wantedRestartGeneration = wantedRestartGeneration;
            this.currentRestartGeneration = currentRestartGeneration;
        }
    }

    public ContainersForHost(List<DockerContainer> dockerContainers) {
        this.dockerContainers = dockerContainers;
    }

}
