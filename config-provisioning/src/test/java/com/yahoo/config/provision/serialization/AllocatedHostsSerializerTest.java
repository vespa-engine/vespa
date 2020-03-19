// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.serialization;

import com.yahoo.component.Version;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NetworkPorts;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.config.provision.serialization.AllocatedHostsSerializer.fromJson;
import static com.yahoo.config.provision.serialization.AllocatedHostsSerializer.toJson;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class AllocatedHostsSerializerTest {

    @Test
    public void testAllocatedHostsSerialization() throws IOException {
        NodeFlavors configuredFlavors = configuredFlavorsFrom("C/12/45/100", 12, 45, 100, 50, Flavor.Type.BARE_METAL);
        Set<HostSpec> hosts = new LinkedHashSet<>();
        hosts.add(new HostSpec("empty",
                               Optional.empty()));
        hosts.add(new HostSpec("with-aliases",
                               List.of("alias1", "alias2")));
        hosts.add(new HostSpec("allocated",
                               List.of(),
                               Optional.empty(),
                               Optional.of(ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                                  Optional.of("docker.foo.com:4443/vespa/bar"))),
                               Optional.empty(), Optional.empty(), Optional.empty(),
                               Optional.of("docker.foo.com:4443/vespa/bar")));
        hosts.add(new HostSpec("flavor-from-resources-1",
                               Collections.emptyList(), new Flavor(new NodeResources(0.5, 3.1, 4, 1))));
        hosts.add(new HostSpec("flavor-from-resources-2",
                               Collections.emptyList(),
                               Optional.of(new Flavor(new NodeResources(0.5, 3.1, 4, 1, NodeResources.DiskSpeed.slow))),
                               Optional.empty(),
                               Optional.empty(),
                               Optional.empty(),
                               Optional.of(new NodeResources(0.5, 3.1, 4, 1, NodeResources.DiskSpeed.any))));
        hosts.add(new HostSpec("configured-flavor",
                               Collections.emptyList(), configuredFlavors.getFlavorOrThrow("C/12/45/100")));
        hosts.add(new HostSpec("with-version",
                               Collections.emptyList(), Optional.empty(), Optional.empty(), Optional.of(Version.fromString("3.4.5"))));
        hosts.add(new HostSpec("with-ports",
                               Collections.emptyList(), Optional.empty(), Optional.empty(), Optional.empty(),
                               Optional.of(new NetworkPorts(List.of(new NetworkPorts.Allocation(1234, "service1", "configId1", "suffix1"),
                                                                    new NetworkPorts.Allocation(4567, "service2", "configId2", "suffix2"))))));

        assertAllocatedHosts(AllocatedHosts.withHosts(hosts), configuredFlavors);
    }

    private void assertAllocatedHosts(AllocatedHosts expectedHosts, NodeFlavors configuredFlavors) throws IOException {
        AllocatedHosts deserializedHosts = fromJson(toJson(expectedHosts), Optional.of(configuredFlavors));

        assertEquals(expectedHosts, deserializedHosts);
        for (HostSpec expectedHost : expectedHosts.getHosts()) {
            HostSpec deserializedHost = requireHost(expectedHost.hostname(), deserializedHosts);
            assertEquals(expectedHost.hostname(), deserializedHost.hostname());
            assertEquals(expectedHost.membership(), deserializedHost.membership());
            assertEquals(expectedHost.flavor(), deserializedHost.flavor());
            assertEquals(expectedHost.version(), deserializedHost.version());
            assertEquals(expectedHost.networkPorts(), deserializedHost.networkPorts());
            assertEquals(expectedHost.aliases(), deserializedHost.aliases());
            assertEquals(expectedHost.requestedResources(), deserializedHost.requestedResources());
            assertEquals(expectedHost.dockerImageRepo(), deserializedHost.dockerImageRepo());
        }
    }

    private HostSpec requireHost(String hostname, AllocatedHosts hosts) {
        for (HostSpec host : hosts.getHosts())
            if (host.hostname().equals(hostname))
                return host;
        throw new IllegalArgumentException("No host " + hostname + " is present");
    }

    private NodeFlavors configuredFlavorsFrom(String flavorName, double cpu, double mem, double disk, double bandwidth, Flavor.Type type) {
        FlavorsConfig.Builder b = new FlavorsConfig.Builder();
        FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder();
        flavor.name(flavorName);
        flavor.minDiskAvailableGb(disk);
        flavor.minCpuCores(cpu);
        flavor.minMainMemoryAvailableGb(mem);
        flavor.bandwidth(bandwidth);
        flavor.environment(type.name());
        b.flavor(flavor);
        return new NodeFlavors(b.build());
    }

}
