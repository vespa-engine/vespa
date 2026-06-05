// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionContext;
import com.yahoo.config.provision.ProvisionLogger;

import java.util.List;

/**
 * Interface towards the host provisioner used to build a {@link Model}. The difference between this provisioner
 * and {@link com.yahoo.config.provision.Provisioner}, is that this interface only exposes methods needed
 * to build the model.
 *
 * @author Ulf Lilleengen
 */
public interface HostProvisioner {

    /** Allocates a single host for a service */
    // TODO: Remove
    default HostSpec allocateHost(String alias) {
        throw new UnsupportedOperationException("Allocating a single host is not supported");
    }

    @Deprecated // Remove after June 2026
    default List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        return prepare(cluster, capacity, new ProvisionContext.Builder().setLogger(logger).build());
    }

    /**
     * Prepares allocation of a set of hosts with a given type, common id and the amount.
     *
     * @param  cluster the cluster to allocate nodes to
     * @param  capacity the capacity describing the capacity requested
     * @param  context the context in which this provisioning is made
     * @return the specification of the allocated hosts
     */
    default List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionContext context) {
        return prepare(cluster, capacity, context.provisionLogger());
    }

}
