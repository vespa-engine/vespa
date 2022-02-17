// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.util.Objects;

/**
 * Container network modes supported by node-admin.
 *
 * @author hakon
 */
public enum ContainerNetworkMode {

    /** Network Prefix-Translated networking. */
    NPT("vespa-bridge"),

    /** A host running a single container in the host network namespace. */
    HOST_NETWORK("host");

    private final String networkName;

    ContainerNetworkMode(String networkName) {
        this.networkName = Objects.requireNonNull(networkName);
    }

    public String networkName() {
        return networkName;
    }

}
