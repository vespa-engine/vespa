// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.*;

import java.util.List;
import java.util.logging.Level;

/**
 * Interface towards the host provisioner used to build a {@link Model}. The difference between this provisioner
 * and {@link com.yahoo.config.provision.Provisioner}, is that this interface only exposes methods needed
 * to build the model.
 *
 * @author lulf
 */
public interface HostProvisioner {

    /** Allocates a single host for a service */
    // TODO: Remove
    HostSpec allocateHost(String alias);

    /**
     * Prepares allocation of a set of hosts with a given type, common id and the amount.
     *
     * @param  cluster the cluster to allocate nodes to
     * @param  capacity the capacity describing the capacity requested
     * @param  groups the number of groups to divide the nodes into
     * @param  logger a logger to which messages to the deployer may be written
     * @return the specification of the allocated hosts
     */
    List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger);

}