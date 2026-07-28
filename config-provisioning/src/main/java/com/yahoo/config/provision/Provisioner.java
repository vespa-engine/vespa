// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
     * @param capacity the capacity requested
     * @param context the context this request is made in
     * @return the specification of the hosts allocated
     */
    List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionContext context);

    /** Activates the allocation of nodes to this application captured in the 'hosts' argument. */
    // TODO: Remove after August 2026
    @Deprecated
    default void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction) {
        var hostsByCluster = hosts.stream().collect(Collectors.groupingBy(host -> host.membership().get().id()));
        var clusterHosts = hostsByCluster.values().stream()
                                         .map(hostsInCluster -> new ClusterHosts(hostsInCluster.get(0).membership().get().cluster(),
                                                                                 hostsInCluster))
                                         .toList();
        activate(clusterHosts, context, transaction);
    }

    default void activate(List<ClusterHosts> clusterHosts, ActivationContext context, ApplicationTransaction transaction) {
        activate(clusterHosts.stream().flatMap(cluster -> cluster.hosts().stream()).toList(), context, transaction);
    }

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
