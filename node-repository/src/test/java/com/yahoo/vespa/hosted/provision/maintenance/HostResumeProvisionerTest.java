// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class HostResumeProvisionerTest {

    private final List<Flavor> flavors = FlavorConfigBuilder.createDummies("default").getFlavors();
    private final MockNameResolver nameResolver = new MockNameResolver();
    private final Zone zone = new Zone(Cloud.builder().dynamicProvisioning(true).allowHostSharing(false).build(),
                                       SystemName.defaultSystem(),
                                       Environment.dev,
                                       RegionName.defaultName());
    private final MockHostProvisioner hostProvisioner = new MockHostProvisioner(flavors, nameResolver, 0);
    private final ProvisioningTester tester = new ProvisioningTester.Builder()
            .zone(zone)
            .hostProvisioner(hostProvisioner)
            .nameResolver(nameResolver)
            .flavors(flavors)
            .build();
    private final HostResumeProvisioner hostResumeProvisioner = new HostResumeProvisioner(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), hostProvisioner);

    @Test
    public void delegates_to_host_provisioner_and_writes_back_result() {
        deployApplication();

        Node host = tester.nodeRepository().nodes().node("host100").orElseThrow();
        Node node = tester.nodeRepository().nodes().node("host100-1").orElseThrow();
        assertTrue("No IP addresses assigned",
                Stream.of(host, node).map(n -> n.ipConfig().primary()).allMatch(Set::isEmpty));

        Node hostNew = host.with(host.ipConfig().withPrimary(Set.of("::100:0")).withPool(host.ipConfig().pool().withIpAddresses(Set.of("::100:1", "::100:2"))));
        Node nodeNew = node.with(IP.Config.ofEmptyPool(Set.of("::100:1")));

        hostResumeProvisioner.maintain();
        assertEquals(hostNew.ipConfig(), tester.nodeRepository().nodes().node("host100").get().ipConfig());
        assertEquals(nodeNew.ipConfig(), tester.nodeRepository().nodes().node("host100-1").get().ipConfig());
    }

    @Test
    public void defer_writing_ip_addresses_until_dns_resolves() {
        deployApplication();
        hostProvisioner.with(MockHostProvisioner.Behaviour.failDnsUpdate);

        Supplier<NodeList> provisioning = () -> tester.nodeRepository().nodes().list(Node.State.provisioned).nodeType(NodeType.host);
        assertEquals(1, provisioning.get().size());
        hostResumeProvisioner.maintain();

        assertTrue("No IP addresses written as DNS updates are failing",
                provisioning.get().stream().allMatch(host -> host.ipConfig().pool().asSet().isEmpty()));

        hostProvisioner.without(MockHostProvisioner.Behaviour.failDnsUpdate);
        hostResumeProvisioner.maintain();
        assertTrue("IP addresses written as DNS updates are succeeding",
                provisioning.get().stream().noneMatch(host -> host.ipConfig().pool().asSet().isEmpty()));
    }

    @Test
    public void correctly_fails_if_irrecoverable_failure() {
        deployApplication();
        hostProvisioner.with(MockHostProvisioner.Behaviour.failProvisioning);

        Node host = tester.nodeRepository().nodes().node("host100").orElseThrow();
        Node node = tester.nodeRepository().nodes().node("host100-1").orElseThrow();
        assertTrue("No IP addresses assigned",
                Stream.of(host, node).map(n -> n.ipConfig().primary()).allMatch(Set::isEmpty));

        hostResumeProvisioner.maintain();
        assertEquals(Set.of("host100", "host100-1"), tester.nodeRepository().nodes().list(Node.State.failed).hostnames());
    }

    private void deployApplication() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("cluster1")).vespaVersion(Version.fromString("7")).build();
        Capacity capacity = Capacity.from(new ClusterResources(1, 1, new NodeResources(1, 30, 20, 3)));
        tester.prepare(ProvisioningTester.applicationId(), cluster, capacity);
        assertEquals(2, tester.nodeRepository().nodes().list().size());
    }
}
