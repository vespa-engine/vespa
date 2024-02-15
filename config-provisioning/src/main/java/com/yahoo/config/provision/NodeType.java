// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Optional;

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
    private final Optional<NodeType> childNodeType;

    public static Optional<NodeType> ofOptional(String name) {
        for (var type : values()) {
            if (type.name().equals(name)) return Optional.of(type);
        }
        return Optional.empty();
    }

    NodeType(String description) {
        this(description, null);
    }

    NodeType(String description, NodeType childNodeTypes) {
        this.childNodeType = Optional.ofNullable(childNodeTypes);
        this.description = description;
    }

    public boolean isHost() {
        return childNodeType.isPresent();
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
     * @return {@link NodeType} of the node that run on this host
     * @throws IllegalStateException if this type is not a host
     */
    public NodeType childNodeType() {
        return childNodeType.orElseThrow(() -> new IllegalStateException(this + " is not a host"));
    }

    /** Returns whether given node type can run on this */
    public boolean canRun(NodeType type) {
        return childNodeType.map(t -> t == type).orElse(false);
    }

    /** Returns the parent host type. */
    public NodeType parentNodeType() {
        for (var type : values()) {
            if (type.canRun(this)) return type;
        }
        throw new IllegalStateException(this + " has no parent");
    }

    /** Returns the host type of this */
    public NodeType hostType() {
        if (isHost()) return this;
        for (NodeType type : values()) {
            if (type.canRun(this)) return type;
        }
        throw new IllegalStateException("No host of " + this + " exists");
    }

}
