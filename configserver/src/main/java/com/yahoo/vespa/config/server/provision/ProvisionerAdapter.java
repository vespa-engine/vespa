// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.provision;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.NetworkPorts;

import java.util.*;

/**
 * A wrapper for {@link Provisioner} to avoid having to expose multitenant
 * behavior to the config model. Adapts interface from a {@link HostProvisioner} to a
 * {@link Provisioner}.
 *
 * @author Ulf Lilleengen
 */
public class ProvisionerAdapter implements HostProvisioner {

    private final Provisioner provisioner;
    private final ApplicationId applicationId;

    public ProvisionerAdapter(Provisioner provisioner, ApplicationId applicationId) {
        this.provisioner = provisioner;
        this.applicationId = applicationId;
    }

    @Override
    public HostSpec allocateHost(String alias) {
        // Wow. Such mess. TODO: Actually support polymorphy or stop pretending to, see also ModelContextImpl.getHostProvisioner
        throw new UnsupportedOperationException("Allocating a single host in a hosted environment is not possible");
    }

    @Override
    @Deprecated // TODO: Remove after April 2020
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger) {
        return provisioner.prepare(applicationId, cluster, capacity.withGroups(groups), logger);
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        return provisioner.prepare(applicationId, cluster, capacity, logger);
    }

}
