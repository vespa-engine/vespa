// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Optional;

/**
 * @author bratseth
 */
public class Cluster {

    private ClusterSpec.Id id;
    private ClusterResources min;
    private ClusterResources max;
    private ClusterResources current;
    private Optional<ClusterResources> target;
    private Optional<ClusterResources> suggested;

    public Cluster(ClusterSpec.Id id,
                   ClusterResources min,
                   ClusterResources max,
                   ClusterResources current,
                   Optional<ClusterResources> target,
                   Optional<ClusterResources> suggested) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.current = current;
        this.target = target;
        this.suggested = suggested;
    }

    public ClusterSpec.Id id() { return id; }
    public ClusterResources min() { return min; }
    public ClusterResources max() { return max; }
    public ClusterResources current() { return current; }
    public Optional<ClusterResources> target() { return target; }
    public Optional<ClusterResources> suggested() { return suggested; }

    @Override
    public String toString() {
        return "cluster '" + id + "'";
    }

}
