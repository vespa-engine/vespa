// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Collection;
import java.util.List;

/**
 * Interface used by the config system to acquire hosts.
 *
 * @author Ulf Lilleengen
 */
public interface Provisioner {

    @Deprecated // Remove after June 2026
    default List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        return prepare(applicationId, cluster, capacity, new ProvisionContext.Builder().setLogger(logger).build());
    }

    /**
     * Prepares allocation of a set of hosts with a given type, common id and the amount.
     *
     * @param applicationId the application requesting hosts
     * @param cluster the specification of the cluster to allocate nodes for
     * @param capacity the capacity requested
     * @param context the context this request is made in
     * @return the specification of the hosts allocated
     */
    default List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionContext context) {
        return prepare(applicationId, cluster, capacity, context.provisionLogger());
    }

    /** Activates the allocation of nodes to this application captured in the 'hosts' argument. */
    void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction);

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
