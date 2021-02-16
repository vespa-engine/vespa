// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.provision;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;

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
    private final Provisioned provisioned;

    public ProvisionerAdapter(Provisioner provisioner, ApplicationId applicationId, Provisioned provisioned) {
        this.provisioner = provisioner;
        this.applicationId = applicationId;
        this.provisioned = provisioned;
    }

    @Override
    public HostSpec allocateHost(String alias) {
        // TODO: Remove this method since hosted/non-hosted needs different interfaces. See also ModelContextImpl.getHostProvisioner
        throw new UnsupportedOperationException("Clusters in hosted environments must have a <nodes count='N'> tag " +
                                                "matching all zones, and having no <node> subtags, " +
                                                "see https://cloud.vespa.ai/en/reference/services");
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        provisioned.add(cluster.id(), capacity);
        return provisioner.prepare(applicationId, cluster, capacity, logger);
    }

}
