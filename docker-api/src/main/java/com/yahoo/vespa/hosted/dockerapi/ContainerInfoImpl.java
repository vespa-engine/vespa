// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.command.InspectContainerResponse;

import java.util.Optional;

class ContainerInfoImpl implements Docker.ContainerInfo {

    private final ContainerName containerName;
    private final InspectContainerResponse inspectContainerResponse;

    ContainerInfoImpl(ContainerName containerName, InspectContainerResponse inspectContainerResponse) {
        this.containerName = containerName;
        this.inspectContainerResponse = inspectContainerResponse;
    }

    @Override
    public Optional<Integer> getPid() {
        InspectContainerResponse.ContainerState state = inspectContainerResponse.getState();
        if (state.getRunning()) {
            Integer containerPid = state.getPid();
            if (containerPid == null) {
                throw new RuntimeException("PID of running container " + containerName + " is null");
            }

            return Optional.of(containerPid);
        }

        return Optional.empty();
    }
}
