// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;

/**
 * The possible types of nodes in the node repository
 * 
 * @author bratseth
 */
public enum NodeType {

    /** A node to be assigned to a tenant to run application workloads */
    tenant("Tenant node"),

    /** A host of a set of (Docker) tenant nodes */
    host("Tenant docker host", tenant),

    /** Nodes running the shared proxy layer */
    proxy("Proxy node"),

    /** A host of a (Docker) proxy node */
    proxyhost("Proxy docker host", proxy),

    /** A config server */
    config("Config server"),

    /** A host of a (Docker) config server node */
    confighost("Config docker host", config),

    /** A controller */
    controller("Controller"),

    /** A host of a (Docker) controller node */
    controllerhost("Controller host", controller),

    /** A host of multiple nodes, only used in {@link SystemName#dev} */
    devhost("Dev host", config, controller, tenant);

    private final List<NodeType> childNodeTypes;
    private final String description;

    NodeType(String description, NodeType... childNodeTypes) {
        this.childNodeTypes = List.of(childNodeTypes);
        this.description = description;
    }

    public boolean isDockerHost() {
        return !childNodeTypes.isEmpty();
    }

    public String description() {
        return description;
    }

    /**
     * @return {@link NodeType} of the node(s) that run on this host
     * @throws IllegalStateException if this type is not a host
     */
    public NodeType childNodeType() {
        return childNodeTypes().get(0);
    }

    /**
     * @return all {@link NodeType}s that can run on this host
     * @throws IllegalStateException if this type is not a host
     */
    public List<NodeType> childNodeTypes() {
        if (! isDockerHost())
            throw new IllegalStateException(this + " has no children");
        return childNodeTypes;
    }

}
