// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Objects;

/**
 * Identifies a cluster in an application.
 *
 * @author mpolden
 */
public class ClusterId {

    private final ApplicationId application;
    private final ClusterSpec.Id cluster;

    public ClusterId(ApplicationId application, ClusterSpec.Id cluster) {
        this.application = Objects.requireNonNull(application);
        this.cluster = Objects.requireNonNull(cluster);
    }

    public ApplicationId application() {
        return application;
    }

    public ClusterSpec.Id cluster() {
        return cluster;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterId that = (ClusterId) o;
        return application.equals(that.application) && cluster.equals(that.cluster);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, cluster);
    }

    @Override
    public String toString() {
        return cluster + " of " + application;
    }

}
