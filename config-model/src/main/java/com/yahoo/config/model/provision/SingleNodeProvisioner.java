// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.net.HostName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A host provisioner used when there is no hosts.xml file (using localhost as the only host)
 * No state in this provisioner, i.e it does not know anything about the active
 * application if one exists.
 *
 * @author hmusum
 */
public class SingleNodeProvisioner implements HostProvisioner {

    private final Host host; // the only host in this system
    private final HostSpec hostSpec;
    private int counter = 0;

    public SingleNodeProvisioner() {
        host = new Host(HostName.getLocalhost());
        this.hostSpec = new HostSpec(host.hostname(), host.aliases(), Optional.empty());
    }

    public SingleNodeProvisioner(Flavor flavor) {
        host = new Host(HostName.getLocalhost());
        this.hostSpec = new HostSpec(host.hostname(),
                                     flavor.resources(), flavor.resources(), flavor.resources(),
                                     ClusterMembership.from(ClusterSpec.specification(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).group(ClusterSpec.Group.from(0)).vespaVersion("1").build(), 0),
                                     Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public HostSpec allocateHost(String alias) {
        return hostSpec;
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        List<HostSpec> hosts = new ArrayList<>();
        hosts.add(new HostSpec(host.hostname(),
                               NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
                               ClusterMembership.from(cluster.with(Optional.of(ClusterSpec.Group.from(0))), counter++),
                               Optional.empty(), Optional.empty(), Optional.empty()));
        return hosts;
    }

}
