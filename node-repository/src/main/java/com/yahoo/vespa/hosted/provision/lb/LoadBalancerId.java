// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Objects;

/**
 * Uniquely identifies a load balancer for an application's container cluster.
 *
 * @author mpolden
 */
public class LoadBalancerId {

    private final ApplicationId application;
    private final ClusterSpec.Id cluster;
    private final String serializedForm;

    public LoadBalancerId(ApplicationId application, ClusterSpec.Id cluster) {
        this.application = Objects.requireNonNull(application, "application must be non-null");
        this.cluster = Objects.requireNonNull(cluster, "cluster must be non-null");
        this.serializedForm = serializedForm(application, cluster);
    }

    public ApplicationId application() {
        return application;
    }

    public ClusterSpec.Id cluster() {
        return cluster;
    }

    /** Serialized form of this */
    public String serializedForm() {
        return serializedForm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoadBalancerId that = (LoadBalancerId) o;
        return Objects.equals(application, that.application) &&
               Objects.equals(cluster, that.cluster);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, cluster);
    }

    @Override
    public String toString() {
        return "load balancer " + serializedForm;
    }

    /** Create an instance from a serialized value on the form tenant:application:instance:cluster-id */
    public static LoadBalancerId fromSerializedForm(String value) {
        int lastSeparator = value.lastIndexOf(":");
        ApplicationId application = ApplicationId.fromSerializedForm(value.substring(0, lastSeparator));
        ClusterSpec.Id cluster = ClusterSpec.Id.from(value.substring(lastSeparator + 1));
        return new LoadBalancerId(application, cluster);
    }

    private static String serializedForm(ApplicationId application, ClusterSpec.Id cluster) {
        return application.serializedForm() + ":" + cluster.value();
    }

}
