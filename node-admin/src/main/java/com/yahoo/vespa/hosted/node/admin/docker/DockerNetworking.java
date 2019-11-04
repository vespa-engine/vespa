// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * The types of network setup for the Docker containers.
 *
 * @author hakon
 */
public enum DockerNetworking {

    /** Network Prefix-Translated networking. */
    NPT("vespa-bridge"),

    /** A host running a single container in the host network namespace. */
    HOST_NETWORK("host"),

    /** A host running multiple containers in a shared local network. */
    LOCAL("vespa-bridge");

    private final String dockerNetworkMode;
    DockerNetworking(String dockerNetworkMode) {
        this.dockerNetworkMode = dockerNetworkMode;
    }

    public String getDockerNetworkMode() {
        return dockerNetworkMode;
    }

}
