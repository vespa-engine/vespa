// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A recording of the capacity requests issued during a model build.
 * Requests are only recorded here if provision requests are issued to the node repo.
 *
 * @author bratseth
 */
public class Provisioned {

    private final Map<ClusterSpec.Id, ClusterSpec> clusters = new HashMap<>();

    private final Map<ClusterSpec.Id, Capacity> capacities = new HashMap<>();

    public void add(ClusterSpec cluster, Capacity capacity) {
        clusters.put(cluster.id(), cluster);
        capacities.put(cluster.id(), capacity);
    }

    /** Returns an unmodifiable map of all the cluster requests recorded during build of the model this belongs to */
    public Map<ClusterSpec.Id, ClusterSpec> clusters() { return Collections.unmodifiableMap(clusters); }

    /** Returns an unmodifiable map of all the capacity provision requests recorded during build of the model this belongs to */
    public Map<ClusterSpec.Id, Capacity> capacities() { return Collections.unmodifiableMap(capacities); }

    // TODO: Remove after June 2024
    public void add(ClusterSpec.Id id, Capacity capacity) {
        capacities.put(id, capacity);
    }

    /** Returns an unmodifiable map of all the provision requests recorded during build of the model this belongs to */
    // TODO: Remove after June 2024
    public Map<ClusterSpec.Id, Capacity> all() { return Collections.unmodifiableMap(capacities); }

}
