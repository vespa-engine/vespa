// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The possible types of nodes in the node repository
 * 
 * @author bratseth
 */
public enum NodeType {

    /** A node to be assigned to a tenant to run application workloads */
    tenant(false, "Tenant node"),

    /** A host of a set of (Docker) tenant nodes */
    host(true, "Tenant docker host"),

    /** Nodes running the shared proxy layer */
    proxy(false, "Proxy node"),

    /** A host of a (Docker) proxy node */
    proxyhost(true, "Proxy docker host"),

    /** A config server */
    config(false, "Config server"),

    /** A host of a (Docker) config server node */
    confighost(true, "Config docker host"),

    /** A controller */
    controller(false, "Controller"),

    /** A host of a (Docker) controller node */
    controllerhost(true, "Controller host");

    private final boolean isDockerHost;
    private final String description;

    NodeType(boolean isDockerHost, String description) {
        this.isDockerHost = isDockerHost;
        this.description = description;
    }

    public boolean isDockerHost() {
        return isDockerHost;
    }

    public String description() {
        return description;
    }

}
