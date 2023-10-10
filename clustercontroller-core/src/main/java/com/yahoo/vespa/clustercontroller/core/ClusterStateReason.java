// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Explicit reasons for why a cluster has been assigned a particular global state.
 * This only includes reasons that aren't directly possible to infer from diffing
 * two cluster states; i.e. distribution bit changes aren't listed here because
 * they are obvious from direct inspection.
 */
public enum ClusterStateReason {

    TOO_FEW_STORAGE_NODES_AVAILABLE,
    TOO_FEW_DISTRIBUTOR_NODES_AVAILABLE,
    TOO_LOW_AVAILABLE_STORAGE_NODE_RATIO,
    TOO_LOW_AVAILABLE_DISTRIBUTOR_NODE_RATIO,

}
