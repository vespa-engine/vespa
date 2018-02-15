// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The possible types of nodes in the node repository
 * 
 * @author bratseth
 */
public enum NodeType {

    /** A host of a set of (docker) tenant nodes */
    host,

    /** Nodes running the shared proxy layer */
    proxy,

    /** A node to be assigned to a tenant to run application workloads */
    tenant,

    /** A config server */
    config,

    /** A host of a (docker) config server node */
    confighost

}
