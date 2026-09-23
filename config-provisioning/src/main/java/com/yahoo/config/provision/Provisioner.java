// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;

/**
 * Interface used by the config system to acquire hosts.
 *
 * @author Ulf Lilleengen
 */
public interface Provisioner {

    /**
     * Prepares allocation of a set of hosts with a given type, common id and the amount.
     *
     * @param applicationId the application requesting hosts
     * @param cluster the specification of the cluster to allocate nodes for
     * @param context the context this request is made in
     * @return the specification of the hosts allocated
     */
    default List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, ProvisionContext context) {
        return prepare(applicationId, cluster, cluster.capacity(), context);
    }

    @Deprecated // TODO: Remove after October 2026
    default List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionContext context) {
        cluster = cluster.builder().capacity(capacity).build();
        return prepare(applicationId, cluster, context);
    }

    void activate(List<ClusterHosts> clusterHosts, ActivationContext context, ApplicationTransaction transaction);

    /** Transactionally remove an application under lock. */
    void remove(ApplicationTransaction transaction);

    /**
     * Requests a restart of the services of the given application
     *
     * @param application the application to restart
     * @param filter a filter which matches the application nodes to restart
     */
    void restart(ApplicationId application, HostFilter filter);

    /** Returns a provision lock for the given application */
    ApplicationMutex lock(ApplicationId application);

}
