// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;

/**
 * Interface used by the config system to acquire hosts.
 *
 * @author Ulf Lilleengen
 */
public interface Provisioner {

    /** Prepares allocation of a list of clusters in anticipation of subsequent prepares with the same arguments. */
    default void earlyPrepare(ApplicationId applicationId, List<ClusterSpec> clusters, ProvisionContext context) {}

    /**
     * Prepares allocation of a set of hosts with a given type, common id and the amount.
     *
     * @param applicationId the application requesting hosts
     * @param cluster the specification of the cluster to allocate nodes for
     * @param context the context this request is made in
     * @return the specification of the hosts allocated
     */
    List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, ProvisionContext context);

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
