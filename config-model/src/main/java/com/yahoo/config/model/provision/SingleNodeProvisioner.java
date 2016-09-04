// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.*;
import com.yahoo.net.HostName;
import com.yahoo.vespa.model.HostSystem;

import java.util.ArrayList;
import java.util.List;
import java.net.UnknownHostException;

/**
 * A host provisioner used when there is no hosts.xml file (using localhost as the only host)
 * No state in this provisioner, i.e it does not know anything about the active
 * application if one exists.
 *
 * @author hmusum
 * @since 5.11
 */
public class SingleNodeProvisioner implements HostProvisioner {

    private final Host host; // the only host in this system
    private final HostSpec hostSpec;
    private int counter = 0;

    public SingleNodeProvisioner() {
        try {
            host = new Host(HostSystem.lookupCanonicalHostname(HostName.getLocalhost()));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        this.hostSpec = new HostSpec(host.getHostname(), host.getHostAliases());
    }

    @Override
    public HostSpec allocateHost(String alias) {
        return hostSpec;
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger) { // TODO: This should fail if capacity requested is more than 1
        List<HostSpec> hosts = new ArrayList<>();
        hosts.add(new HostSpec(host.getHostname(), host.getHostAliases(), ClusterMembership.from(cluster, counter++)));
        return hosts;
    }

}
