// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.net.HostName;

import java.util.List;
import java.util.Optional;

/**
 * @author bjorncs
 */
public class HostProvisionerWithCustomRealResource implements HostProvisioner {

    private final int vcpu;

    public HostProvisionerWithCustomRealResource(int vcpu) { this.vcpu = vcpu; }
    public HostProvisionerWithCustomRealResource() { this(4); }

    @Override
    public HostSpec allocateHost(String alias) {
        Host host = new Host(HostName.getLocalhost());
        ClusterMembership membership = ClusterMembership.from(
                ClusterSpec
                        .specification(
                                ClusterSpec.Type.container,
                                ClusterSpec.Id.from("id"))
                        .vespaVersion("")
                        .group(ClusterSpec.Group.from(0))
                        .build(),
                0);
        return new HostSpec(
                host.hostname(), new NodeResources(vcpu, 0, 0, 0), NodeResources.unspecified(), NodeResources.unspecified(),
                membership, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) { return List.of(); }
}
