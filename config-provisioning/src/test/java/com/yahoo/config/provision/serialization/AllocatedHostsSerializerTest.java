// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.serialization;

import com.yahoo.component.Version;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NetworkPorts;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.zone.AuthMethod;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.config.provision.serialization.AllocatedHostsSerializer.fromJson;
import static com.yahoo.config.provision.serialization.AllocatedHostsSerializer.toJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class AllocatedHostsSerializerTest {

    private static final NodeResources smallSlowDiskSpeedNode = new NodeResources(0.5, 3.1, 4, 1, NodeResources.DiskSpeed.slow);
    private static final NodeResources bigSlowDiskSpeedNode = new NodeResources(1.0, 6.2, 8, 2, NodeResources.DiskSpeed.slow);
    private static final NodeResources anyDiskSpeedNode = new NodeResources(0.5, 3.1, 4, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.local,
                                                                            NodeResources.Architecture.x86_64, new NodeResources.GpuResources(1, 16));
    private static final NodeResources arm64Node = new NodeResources(0.5, 3.1, 4, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any, NodeResources.Architecture.arm64);

    @Test
    void testAllocatedHostsSerialization() throws IOException {
        Set<HostSpec> hosts = new LinkedHashSet<>();
        hosts.add(new HostSpec("empty", Optional.empty()));
        hosts.add(new HostSpec("with-aliases", Optional.empty()));
        hosts.add(new HostSpec("allocated",
                               smallSlowDiskSpeedNode,
                               bigSlowDiskSpeedNode,
                               anyDiskSpeedNode,
                               ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                      Optional.of(DockerImage.fromString("docker.foo.com:4443/vespa/bar"))),
                               Optional.empty(),
                               Optional.empty(),
                               Optional.of(DockerImage.fromString("docker.foo.com:4443/vespa/bar"))));
        hosts.add(new HostSpec("flavor-from-resources-2",
                               smallSlowDiskSpeedNode,
                               bigSlowDiskSpeedNode,
                               anyDiskSpeedNode,
                               ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                      Optional.empty()),
                               Optional.empty(),
                               Optional.empty(),
                               Optional.empty()));
        hosts.add(new HostSpec("with-version",
                               smallSlowDiskSpeedNode,
                               bigSlowDiskSpeedNode,
                               anyDiskSpeedNode,
                               ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                      Optional.empty()),
                               Optional.of(Version.fromString("3.4.5")),
                               Optional.empty(), Optional.empty()));
        hosts.add(new HostSpec("with-load-balancer-settings",
                               smallSlowDiskSpeedNode,
                               bigSlowDiskSpeedNode,
                               anyDiskSpeedNode,
                               ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                      Optional.empty(), new ZoneEndpoint(true, true, List.of(AuthMethod.mtls, AuthMethod.token), List.of(new AllowedUrn(AccessType.awsPrivateLink, "burn")))),
                               Optional.empty(),
                               Optional.empty(),
                               Optional.empty()));
        hosts.add(new HostSpec("with-ports",
                               smallSlowDiskSpeedNode,
                               bigSlowDiskSpeedNode,
                               anyDiskSpeedNode,
                               ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                      Optional.empty()),
                               Optional.empty(),
                               Optional.of(new NetworkPorts(List.of(new NetworkPorts.Allocation(1234, "service1", "configId1", "suffix1"),
                                                      new NetworkPorts.Allocation(4567, "service2", "configId2", "suffix2")))),
                               Optional.empty()));
        hosts.add(new HostSpec("arm64",
                               arm64Node,
                               arm64Node,
                               arm64Node,
                               ClusterMembership.from("container/test/0/0", Version.fromString("6.73.1"),
                                                      Optional.empty()),
                               Optional.empty(),
                               Optional.of(new NetworkPorts(List.of(new NetworkPorts.Allocation(1234, "service1", "configId1", "suffix1"),
                                                      new NetworkPorts.Allocation(4567, "service2", "configId2", "suffix2")))),
                               Optional.empty()));

        assertAllocatedHosts(AllocatedHosts.withHosts(hosts));
    }

    private void assertAllocatedHosts(AllocatedHosts expectedHosts) throws IOException {
        AllocatedHosts deserializedHosts = fromJson(toJson(expectedHosts));

        assertEquals(expectedHosts, deserializedHosts);
        for (HostSpec expectedHost : expectedHosts.getHosts()) {
            HostSpec deserializedHost = requireHost(expectedHost.hostname(), deserializedHosts);
            assertEquals(expectedHost.hostname(), deserializedHost.hostname());
            assertEquals(expectedHost.membership(), deserializedHost.membership());
            assertEquals(expectedHost.realResources(), deserializedHost.realResources());
            assertEquals(expectedHost.advertisedResources(), deserializedHost.advertisedResources());
            assertEquals(expectedHost.requestedResources(), deserializedHost.requestedResources());
            assertEquals(expectedHost.version(), deserializedHost.version());
            assertEquals(expectedHost.networkPorts(), deserializedHost.networkPorts());
            assertEquals(expectedHost.dockerImageRepo(), deserializedHost.dockerImageRepo());
        }
    }

    private HostSpec requireHost(String hostname, AllocatedHosts hosts) {
        for (HostSpec host : hosts.getHosts())
            if (host.hostname().equals(hostname))
                return host;
        throw new IllegalArgumentException("No host " + hostname + " is present");
    }

}
