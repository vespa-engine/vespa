// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;

/**
 * The possible types of nodes in the node repository
 * 
 * @author bratseth
 */
public enum NodeType {

    /** Node assignable to a tenant to run application workloads */
    tenant("Tenant node"),

    /** Host of a tenant nodes */
    host("Tenant host", tenant),

    /** Node serving the shared proxy layer */
    proxy("Proxy node"),

    /** Host of a proxy node */
    proxyhost("Proxy host", proxy),

    /** Config server node */
    config("Config server node"),

    /** Host of a config server node */
    confighost("Config server host", config),

    /** Controller node */
    controller("Controller node"),

    /** Host of a controller node */
    controllerhost("Controller host", controller);

    private final String description;
    private final List<NodeType> childNodeTypes;

    NodeType(String description, NodeType... childNodeTypes) {
        this.childNodeTypes = List.of(childNodeTypes);
        this.description = description;
    }

    public boolean isHost() {
        return !childNodeTypes.isEmpty();
    }

    /** either config server or controller */
    public boolean isConfigServerLike() {
        return this == config || this == controller;
    }

    /** either config server host or controller host */
    public boolean isConfigServerHostLike() {
        return this == confighost || this == controllerhost;
    }

    /** Returns whether this supports host sharing */
    public boolean isSharable() {
        return this == NodeType.host;
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
        if (! isHost())
            throw new IllegalStateException(this + " has no children");
        return childNodeTypes;
    }

    /** Returns whether given node type can run on this */
    public boolean canRun(NodeType type) {
        return childNodeTypes.contains(type);
    }

    /** Returns the host type of this */
    public NodeType hostType() {
        if (isHost()) return this;
        for (NodeType nodeType : values()) {
            // Ignore host types that support multiple node types
            if (nodeType.childNodeTypes.size() == 1 && nodeType.canRun(this)) {
                return nodeType;
            }
        }
        throw new IllegalArgumentException("No host of " + this + " exists");
    }

}
