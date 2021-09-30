// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The client-side version of the node repository's view of applications
 *
 * @author bratseth
 */
public class Application {

    private final ApplicationId id;
    private final Map<ClusterSpec.Id, Cluster> clusters;

    public Application(ApplicationId id, Collection<Cluster> clusters) {
        this.id = id;
        this.clusters = clusters.stream().collect(Collectors.toUnmodifiableMap(c -> c.id(), c -> c));
    }

    public ApplicationId id() { return id; }

    public Map<ClusterSpec.Id, Cluster> clusters() { return clusters; }

    @Override
    public String toString() {
        return "application '" + id + "'";
    }

}
