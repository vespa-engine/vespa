// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The possible types of nodes in the node repository
 * 
 * @author bratseth
 */
public enum NodeType {

    /** A node to be assigned to a tenant to run application workloads */
    tenant(null, "Tenant node"),

    /** A host of a set of (Docker) tenant nodes */
    host(tenant, "Tenant docker host"),

    /** Nodes running the shared proxy layer */
    proxy(null, "Proxy node"),

    /** A host of a (Docker) proxy node */
    proxyhost(proxy, "Proxy docker host"),

    /** A config server */
    config(null, "Config server"),

    /** A host of a (Docker) config server node */
    confighost(config, "Config docker host"),

    /** A controller */
    controller(null, "Controller"),

    /** A host of a (Docker) controller node */
    controllerhost(controller, "Controller host");

    private final NodeType childNodeType;
    private final String description;

    NodeType(NodeType childNodeType, String description) {
        this.childNodeType = childNodeType;
        this.description = description;
    }

    public boolean isDockerHost() {
        return childNodeType != null;
    }

    public String description() {
        return description;
    }

    /**
     * @return {@link NodeType} of the node(s) that run on this host
     * @throws IllegalStateException if this type is not a host
     */
    public NodeType childNodeType() {
        if (! isDockerHost())
            throw new IllegalStateException(this + " has no children");

        return childNodeType;
    }
}
