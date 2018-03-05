// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The possible types of nodes in the node repository
 * 
 * @author bratseth
 */
public enum NodeType {

    /** A host of a set of (docker) tenant nodes */
    host(true),

    /** Nodes running the shared proxy layer */
    proxy(false),

    /** A host of a (docker) proxy node */
    proxyhost(true),

    /** A node to be assigned to a tenant to run application workloads */
    tenant(false),

    /** A config server */
    config(false),

    /** A host of a (docker) config server node */
    confighost(true);

    private boolean isDockerHost;

    NodeType(boolean isDockerHost) {
        this.isDockerHost = isDockerHost;
    }

    public boolean isDockerHost() {
        return isDockerHost;
    }
}
