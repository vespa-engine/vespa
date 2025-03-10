// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NetworkPorts;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.test.ManualClock;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.TrustStoreItem;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.Architecture;
import static com.yahoo.config.provision.NodeResources.DiskSpeed;
import static com.yahoo.config.provision.NodeResources.StorageType;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 */
public class NodeSerializerTest {

    private final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "large", "ugccloud-container", "arm64", "gpu");
    private final NodeSerializer nodeSerializer = new NodeSerializer(nodeFlavors, CloudAccount.from("aws:999123456789"));
    private final ManualClock clock = new ManualClock();

    @Test
    public void provisioned_node_serialization() {
        Node node = createNode();

        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(node.hostname(), copy.hostname());
        assertEquals(node.id(), copy.id());
        assertEquals(node.state(), copy.state());
        assertFalse(copy.allocation().isPresent());
        assertEquals(0, copy.history().events().size());
    }

    @Test
    public void reserved_node_serialization() {
        Node node = createNode();
        NodeResources requestedResources = new NodeResources(1.2, 3.4, 5.6, 7.8,
                                                             DiskSpeed.any, StorageType.any, Architecture.arm64,
                                                             new NodeResources.GpuResources(1, 16));

        assertEquals(0, node.history().events().size());
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                                                ApplicationName.from("myApplication"),
                                                InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0/0/stateful", Vtag.currentVersion, Optional.empty()),
                             requestedResources,
                             Instant.ofEpochMilli(1));
        assertEquals(1, node.history().events().size());
        node = node.withExtraId(Optional.of("foobarbaz"));
        node = node.withRestart(new Generation(1, 2));
        node = node.withReboot(new Generation(3, 4));
        node = node.with(FlavorConfigBuilder.createDummies("arm64").getFlavorOrThrow("arm64"), Agent.system, Instant.ofEpochMilli(2));
        node = node.with(node.status().withVespaVersion(Version.fromString("1.2.3")));
        node = node.with(node.status().withIncreasedFailCount().withIncreasedFailCount());
        node = node.with(NodeType.tenant);
        node = node.downAt(Instant.ofEpochMilli(5), Agent.system)
                   .upAt(Instant.ofEpochMilli(6), Agent.system)
                   .downAt(Instant.ofEpochMilli(7), Agent.system);
        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));

        assertEquals(node.id(), copy.id());
        assertEquals(node.extraId(), copy.extraId());
        assertEquals(node.hostname(), copy.hostname());
        assertEquals(node.state(), copy.state());
        assertEquals(1, copy.allocation().get().restartGeneration().wanted());
        assertEquals(2, copy.allocation().get().restartGeneration().current());
        assertEquals(3, copy.status().reboot().wanted());
        assertEquals(4, copy.status().reboot().current());
        assertEquals("arm64", copy.flavor().name());
        assertEquals(Architecture.arm64.name(), copy.resources().architecture().name());
        assertEquals("1.2.3", copy.status().vespaVersion().get().toString());
        assertEquals(2, copy.status().failCount());
        assertEquals(node.allocation().get().owner(), copy.allocation().get().owner());
        assertEquals(node.allocation().get().membership(), copy.allocation().get().membership());
        assertEquals(node.allocation().get().requestedResources(), copy.allocation().get().requestedResources());
        assertEquals(node.allocation().get().removable(), copy.allocation().get().removable());
        assertEquals(4, copy.history().events().size());
        assertEquals(5, copy.history().log().size());
        assertEquals(Instant.ofEpochMilli(1), copy.history().event(History.Event.Type.reserved).get().at());
        assertEquals(new History.Event(History.Event.Type.down, Agent.system, Instant.ofEpochMilli(7)),
                     copy.history().log().get(copy.history().log().size() - 1));
        assertEquals(NodeType.tenant, copy.type());
    }

    @Test
    public void reboot_and_restart_and_type_no_current_values_serialization() {
        String nodeData = 
                "{\n" +
                "   \"type\" : \"tenant\",\n" +
                "   \"state\" : \"provisioned\",\n" +
                "   \"rebootGeneration\" : 1,\n" +
                "   \"currentRebootGeneration\" : 2,\n" +
                "   \"flavor\" : \"large\",\n" +
                "   \"history\" : [\n" +
                "      {\n" +
                "         \"type\" : \"provisioned\",\n" +
                "         \"at\" : 1444391401389,\n" +
                "         \"agent\" : \"system\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"type\" : \"reserved\",\n" +
                "         \"at\" : 1444391402611,\n" +
                "         \"agent\" : \"system\"\n" +
                "      }\n" +
                "   ],\n" +
                "   \"instance\" : {\n" +
                "      \"applicationId\" : \"myApplication\",\n" +
                "      \"tenantId\" : \"myTenant\",\n" +
                "      \"instanceId\" : \"myInstance\",\n" +
                "      \"serviceId\" : \"content/myId/0/0/stateful\",\n" +
                "      \"restartGeneration\" : 3,\n" +
                "      \"currentRestartGeneration\" : 4,\n" +
                "      \"removable\" : true,\n" +
                "      \"wantedVespaVersion\": \"6.42.2\"\n" +
                "   },\n" +
                "   \"openStackId\" : \"myId\",\n" +
                "   \"hostname\" : \"myHostname\",\n" +
                "   \"ipAddresses\" : [\"127.0.0.1\"]\n" +
                "}";

        Node node = nodeSerializer.fromJson(Utf8.toBytes(nodeData));

        assertEquals("large", node.flavor().name());
        assertEquals(1, node.status().reboot().wanted());
        assertEquals(2, node.status().reboot().current());
        assertEquals(3, node.allocation().get().restartGeneration().wanted());
        assertEquals(4, node.allocation().get().restartGeneration().current());
        assertEquals(List.of(History.Event.Type.provisioned, History.Event.Type.reserved),
                     node.history().events().stream().map(History.Event::type).toList());
        assertTrue(node.allocation().get().removable());
        assertEquals(NodeType.tenant, node.type());
    }

    @Test
    public void retired_node_serialization() {
        Node node = createNode();

        clock.advance(Duration.ofMinutes(3));
        assertEquals(0, node.history().events().size());
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                                                ApplicationName.from("myApplication"),
                                                InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0/0/stateful", Vtag.currentVersion, Optional.empty()),
                             node.flavor().resources(),
                             clock.instant());
        assertEquals(1, node.history().events().size());
        clock.advance(Duration.ofMinutes(2));
        node = node.retire(Agent.application, clock.instant());
        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(2, copy.history().events().size());
        assertEquals(clock.instant().truncatedTo(MILLIS), copy.history().event(History.Event.Type.retired).get().at());
        assertEquals(Agent.application,
                     (copy.history().event(History.Event.Type.retired).get()).agent());
        assertTrue(copy.allocation().get().membership().retired());

        Node removable = copy.with(node.allocation().get().removable(true, true));
        Node removableCopy = nodeSerializer.fromJson( nodeSerializer.toJson(removable));
        assertTrue(removableCopy.allocation().get().removable());
        assertTrue(removableCopy.allocation().get().reusable());
    }

    @Test
    public void assimilated_node_deserialization() {
        Node node = nodeSerializer.fromJson(("""
                {
                  "type": "tenant",
                  "hostname": "assimilate2.vespahosted.yahoo.tld",
                  "state": "provisioned",
                  "ipAddresses": ["127.0.0.1"],
                  "openStackId": "",
                  "flavor": "ugccloud-container",
                  "instance": {
                    "tenantId": "by_mortent",
                    "applicationId": "ugc-assimilate",
                    "instanceId": "default",
                    "serviceId": "container/ugccloud-container/0/0",
                    "restartGeneration": 0,
                    "wantedVespaVersion": "6.42.2"
                  }
                }
                """).getBytes());
        assertEquals(0, node.history().events().size());
        assertTrue(node.allocation().isPresent());
        assertEquals("ugccloud-container", node.allocation().get().membership().cluster().id().value());
        assertEquals("container", node.allocation().get().membership().cluster().type().name());
        assertEquals(0, node.allocation().get().membership().cluster().group().get().index());
        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(0, copy.history().events().size());
    }

    @Test
    public void fail_count() {
        Node node = createNode();
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                             ApplicationName.from("myApplication"),
                             InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0/0/stateful", Vtag.currentVersion, Optional.empty()),
                             node.flavor().resources(),
                             clock.instant());

        node = node.with(node.status().withFailCount(0));
        Node copy2 = nodeSerializer.fromJson(nodeSerializer.toJson(node));

        assertEquals(0, copy2.status().failCount());
    }

    @Test
    public void serialize_parent_hostname() {
        final String parentHostname = "parent.yahoo.com";
        Node node = Node.create("myId", IP.Config.of(List.of("127.0.0.1"), List.of()), "myHostname", nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant)
                .parentHostname(parentHostname)
                .build();

        Node deserializedNode = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(parentHostname, deserializedNode.parentHostname().get());
    }


    @Test
    public void serializes_multiple_ip_addresses() {
        byte[] nodeWithMultipleIps = createNodeJson("node4.yahoo.tld", "127.0.0.4", "::4");
        Node deserializedNode = nodeSerializer.fromJson(nodeWithMultipleIps);
        assertEquals(List.of("127.0.0.4", "::4"), deserializedNode.ipConfig().primary());
    }

    @Test
    public void serialize_ip_address_pool() {
        Node node = createNode();

        // Test round-trip with address pool
        node = node.with(node.ipConfig().withPool(IP.Pool.of(
                List.of("::1", "::2", "::3"),
                List.of(HostName.of("a"), HostName.of("b"), HostName.of("c")))));
        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(node.ipConfig(), copy.ipConfig());

        // Test round-trip without address pool (handle empty pool)
        node = createNode();
        copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(node.ipConfig(), copy.ipConfig());
    }

    @Test
    public void want_to_retire_defaults_to_false() {
        String nodeData =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"state\" : \"provisioned\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"]\n" +
                        "}";
        Node node = nodeSerializer.fromJson(Utf8.toBytes(nodeData));
        assertFalse(node.status().wantToRetire());
    }

    @Test
    public void flavor_overrides_serialization() {
        Node node = createNode();
        assertEquals(20, node.flavor().resources().diskGb(), 0);
        node = node.with(node.flavor().with(FlavorOverrides.ofDisk(1234)), Agent.system, clock.instant());
        assertEquals(1234, node.flavor().resources().diskGb(), 0);

        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(1234, copy.flavor().resources().diskGb(), 0);
        assertEquals(node, copy);
        assertTrue(node.history().event(History.Event.Type.resized).isPresent());
    }

    @Test
    public void want_to_deprovision_defaults_to_false() {
        String nodeData =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"state\" : \"provisioned\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"]\n" +
                        "}";
        Node node = nodeSerializer.fromJson(Utf8.toBytes(nodeData));
        assertFalse(node.status().wantToDeprovision());
    }

    @Test
    public void want_to_rebuild_and_upgrade_flavor() {
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertFalse(node.status().wantToRebuild());
        node = node.with(node.status().withWantToRetire(true, false, true, true));
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertTrue(node.status().wantToRetire());
        assertFalse(node.status().wantToDeprovision());
        assertTrue(node.status().wantToRebuild());
        assertTrue(node.status().wantToUpgradeFlavor());
    }

    @Test
    public void vespa_version_serialization() {
        String nodeWithWantedVespaVersion =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"state\" : \"provisioned\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"],\n" +
                        "   \"instance\": {\n" +
                        "     \"tenantId\":\"t\",\n" +
                        "     \"applicationId\":\"a\",\n" +
                        "     \"instanceId\":\"i\",\n" +
                        "     \"serviceId\": \"content/myId/0/0/stateful\",\n" +
                        "     \"wantedVespaVersion\": \"6.42.2\"\n" +
                        "   }\n" +
                        "}";
        Node node = nodeSerializer.fromJson(Utf8.toBytes(nodeWithWantedVespaVersion));
        assertEquals("6.42.2", node.allocation().get().membership().cluster().vespaVersion().toString());
    }

    @Test
    public void os_version_serialization() {
        Node serialized = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertFalse(serialized.status().osVersion().current().isPresent());

        // Update OS version
        serialized = serialized.withCurrentOsVersion(Version.fromString("7.1"), Instant.ofEpochMilli(42));
        assertFalse("No event is added when initial version is set",
                    serialized.history().event(History.Event.Type.osUpgraded).isPresent());
        serialized = serialized.withCurrentOsVersion(Version.fromString("7.2"), Instant.ofEpochMilli(123))
                               .withCurrentOsVersion(Version.fromString("7.2"), Instant.ofEpochMilli(456));
        serialized = nodeSerializer.fromJson(nodeSerializer.toJson(serialized));
        assertEquals(Version.fromString("7.2"), serialized.status().osVersion().current().get());
        var osUpgradedEvents = serialized.history().events().stream()
                                         .filter(event -> event.type() == History.Event.Type.osUpgraded)
                                         .toList();
        assertEquals("OS upgraded event is added", 1, osUpgradedEvents.size());
        assertEquals("Duplicate updates of same version uses earliest instant", Instant.ofEpochMilli(123),
                     osUpgradedEvents.get(0).at());
    }

    @Test
    public void firmware_check_serialization() {
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertFalse(node.status().firmwareVerifiedAt().isPresent());

        node = node.withFirmwareVerifiedAt(Instant.ofEpochMilli(100));
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(100, node.status().firmwareVerifiedAt().get().toEpochMilli());
        assertEquals(Instant.ofEpochMilli(100), node.history().event(History.Event.Type.firmwareVerified).get().at());
    }

    @Test
    public void serialize_node_types() {
        for (NodeType t : NodeType.values()) {
            assertEquals(t, NodeSerializer.nodeTypeFromString(NodeSerializer.toString(t)));
        }
    }

    @Test
    public void reports_serialization() {
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertTrue(node.reports().isEmpty());

        var slime = new Slime();
        Cursor reportCursor = slime.setObject();
        reportCursor.setLong(Report.CREATED_FIELD, 3);
        reportCursor.setString(Report.DESCRIPTION_FIELD, "desc");
        reportCursor.setLong("value", 4);
        final String reportId = "rid";
        var report = Report.fromSlime(reportId, reportCursor);
        var reports = new Reports.Builder().setReport(report).build();

        node = node.with(reports);
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));

        reports = node.reports();
        assertFalse(reports.isEmpty());
        assertEquals(1, reports.getReports().size());
        report = reports.getReport(reportId).orElseThrow();
        assertEquals(reportId, report.getReportId());
        assertEquals(Instant.ofEpochMilli(3), report.getCreatedTime());
        assertEquals("desc", report.getDescription());
        assertEquals(4, report.getInspector().field("value").asLong());
        assertEquals(Type.NIX, report.getInspector().field("bogus").type());
    }

    @Test
    public void model_id_serialization() {
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertFalse(node.modelName().isPresent());

        node = node.withModelName("some model");
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals("some model", node.modelName().get());
    }

    @Test
    public void network_ports_serialization() {
        Node node = createNode();
        List<NetworkPorts.Allocation> list = new ArrayList<>();
        list.add(new NetworkPorts.Allocation(8080, "container", "default/0", "http"));
        list.add(new NetworkPorts.Allocation(19101, "searchnode", "other/1", "rpc"));
        NetworkPorts ports = new NetworkPorts(list);
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                ApplicationName.from("myApplication"),
                InstanceName.from("myInstance")),
                ClusterMembership.from("content/myId/0/0/stateful", Vtag.currentVersion, Optional.empty()),
                node.flavor().resources(),
                clock.instant());
        assertTrue(node.allocation().isPresent());
        node = node.with(node.allocation().get().withNetworkPorts(ports));
        assertTrue(node.allocation().isPresent());
        assertTrue(node.allocation().get().networkPorts().isPresent());
        Node copy = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertTrue(copy.allocation().isPresent());
        assertTrue(copy.allocation().get().networkPorts().isPresent());
        NetworkPorts portsCopy = node.allocation().get().networkPorts().get();
        Collection<NetworkPorts.Allocation> listCopy = portsCopy.allocations();
        assertEquals(list, listCopy);
    }

    @Test
    public void switch_hostname_serialization() {
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertFalse(node.switchHostname().isPresent());
        String switchHostname = "switch0.example.com";
        node = node.withSwitchHostname(switchHostname);
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(switchHostname, node.switchHostname().get());
    }

    @Test
    public void exclusive_to_serialization() {
        Node.Builder builder = Node.create("myId", IP.Config.EMPTY, "myHostname",
                nodeFlavors.getFlavorOrThrow("default"), NodeType.host);
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(builder.build()));
        assertFalse(node.exclusiveToApplicationId().isPresent());
        assertFalse(node.hostTTL().isPresent());
        assertFalse(node.exclusiveToClusterType().isPresent());

        ApplicationId provisionedForApp = ApplicationId.from("tenant1", "app1", "instance1");
        node = nodeSerializer.fromJson(nodeSerializer.toJson(builder.exclusiveToApplicationId(provisionedForApp).build()));
        assertEquals(Optional.of(provisionedForApp), node.exclusiveToApplicationId());
        assertEquals(Optional.empty(), node.provisionedForApplicationId());

        ClusterSpec.Type exclusiveToCluster = ClusterSpec.Type.admin;
        node = builder.provisionedForApplicationId(provisionedForApp)
                      .hostTTL(Duration.ofDays(1))
                      .hostEmptyAt(clock.instant().minus(Duration.ofDays(1)).truncatedTo(MILLIS))
                      .exclusiveToClusterType(exclusiveToCluster).build();
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(provisionedForApp, node.exclusiveToApplicationId().get());
        assertEquals(provisionedForApp, node.provisionedForApplicationId().get());
        assertEquals(Duration.ofDays(1), node.hostTTL().get());
        assertEquals(clock.instant().minus(Duration.ofDays(1)).truncatedTo(MILLIS), node.hostEmptyAt().get());
        assertEquals(exclusiveToCluster, node.exclusiveToClusterType().get());
    }

    @Test
    public void truststore_serialization() {
        Node node = nodeSerializer.fromJson(nodeSerializer.toJson(createNode()));
        assertEquals(List.of(), node.trustedCertificates());
        List<TrustStoreItem> trustStoreItems = List.of(new TrustStoreItem("foo", Instant.parse("2023-09-01T23:59:59Z")), new TrustStoreItem("bar", Instant.parse("2025-05-20T23:59:59Z")));
        node = node.with(trustStoreItems);
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(trustStoreItems, node.trustedCertificates());
    }

    @Test
    public void cloud_account_serialization() {
        CloudAccount account = CloudAccount.from("012345678912");
        Node node = Node.create("id", "host1.example.com", nodeFlavors.getFlavorOrThrow("default"), State.provisioned, NodeType.host)
                        .cloudAccount(account)
                        .provisionedForApplicationId(ApplicationId.defaultId())
                        .build();
        node = nodeSerializer.fromJson(nodeSerializer.toJson(node));
        assertEquals(account, node.cloudAccount());
    }

    private byte[] createNodeJson(String hostname, String... ipAddress) {
        String ipAddressJsonPart = "";
        if (ipAddress.length > 0) {
                ipAddressJsonPart = "\"ipAddresses\":[" +
                        Arrays.stream(ipAddress)
                                .map(ip -> "\"" + ip + "\"")
                                .collect(Collectors.joining(",")) +
                        "],";
        }
        return ("{\"hostname\":\"" + hostname + "\"," +
                ipAddressJsonPart +
                "\"openStackId\":\"myId\"," +
                "\"state\":\"provisioned\"," +
                "\"flavor\":\"default\",\"rebootGeneration\":0," +
                "\"currentRebootGeneration\":0,\"failCount\":0,\"history\":[],\"type\":\"tenant\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private Node createNode() {
        return Node.create("myId",
                           IP.Config.of(List.of("127.0.0.1"), List.of()),
                           "myHostname",
                           nodeFlavors.getFlavorOrThrow("default"),
                           NodeType.tenant).build();
    }

}
