// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.transaction.NestedTransaction;

import java.util.Collection;
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
     * @param capacity the capacity requested
     * @param logger a logger which receives messages which are returned to the requestor
     * @return the specification of the hosts allocated
     */
    List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger);

    /**
     * Activates the allocation of nodes to this application captured in the hosts argument.
     *
     * @param transaction Transaction with operations to commit together with any operations done within the provisioner.
     * @param application The {@link ApplicationId} that was activated.
     * @param hosts a set of {@link HostSpec}.
     */
    // TODO(mpolden): Remove
    void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts);

    /**
     * Activates the allocation of nodes to this application captured in the hosts argument.
     *
     * @param transaction Transaction with operations to commit together with any operations done within the provisioner.
     * @param hosts       a set of {@link HostSpec}.
     * @param lock        A provision lock for the relevant application. This must be held when calling this.
     */
    // TODO: Remove after November 2020
    void activate(NestedTransaction transaction, Collection<HostSpec> hosts, ProvisionLock lock);

    /** Activates the allocation of nodes to this application captured in the hosts argument. */
    void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction);

    /**
     * Transactionally remove this application.
     *
     * @param transaction Transaction with operations to commit together with any operations done within the provisioner.
     * @param application the application to remove
     */
    // TODO(mpolden): Remove
    void remove(NestedTransaction transaction, ApplicationId application);

    /**
     * Transactionally remove application guarded by given lock.
     *
     * @param transaction Transaction with operations to commit together with any operations done within the provisioner.
     * @param lock        A provision lock for the relevant application. This must be held when calling this.
     */
    // TODO: Remove after November 2020
    void remove(NestedTransaction transaction, ProvisionLock lock);

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
    ProvisionLock lock(ApplicationId application);

}
