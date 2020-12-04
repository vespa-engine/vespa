// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
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
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;
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
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 */
public class NodeSerializerTest {

    private final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "large", "ugccloud-container");
    private final NodeSerializer nodeSerializer = new NodeSerializer(nodeFlavors, 1000);
    private final ManualClock clock = new ManualClock();

    @Test
    public void provisioned_node_serialization() {
        Node node = createNode();

        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(node.hostname(), copy.hostname());
        assertEquals(node.id(), copy.id());
        assertEquals(node.state(), copy.state());
        assertFalse(copy.allocation().isPresent());
        assertEquals(0, copy.history().events().size());
    }

    @Test
    public void reserved_node_serialization() {
        Node node = createNode();
        NodeResources requestedResources = new NodeResources(1.2, 3.4, 5.6, 7.8, NodeResources.DiskSpeed.any);

        clock.advance(Duration.ofMinutes(3));
        assertEquals(0, node.history().events().size());
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                                                ApplicationName.from("myApplication"),
                                                InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0/0/stateful", Vtag.currentVersion, Optional.empty()),
                             requestedResources,
                             clock.instant());
        assertEquals(1, node.history().events().size());
        node = node.withRestart(new Generation(1, 2));
        node = node.withReboot(new Generation(3, 4));
        node = node.with(FlavorConfigBuilder.createDummies("large").getFlavorOrThrow("large"));
        node = node.with(node.status().withVespaVersion(Version.fromString("1.2.3")));
        node = node.with(node.status().withIncreasedFailCount().withIncreasedFailCount());
        node = node.with(NodeType.tenant);
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));

        assertEquals(node.id(), copy.id());
        assertEquals(node.hostname(), copy.hostname());
        assertEquals(node.state(), copy.state());
        assertEquals(1, copy.allocation().get().restartGeneration().wanted());
        assertEquals(2, copy.allocation().get().restartGeneration().current());
        assertEquals(3, copy.status().reboot().wanted());
        assertEquals(4, copy.status().reboot().current());
        assertEquals("large", copy.flavor().name());
        assertEquals("1.2.3", copy.status().vespaVersion().get().toString());
        assertEquals(2, copy.status().failCount());
        assertEquals(node.allocation().get().owner(), copy.allocation().get().owner());
        assertEquals(node.allocation().get().membership(), copy.allocation().get().membership());
        assertEquals(node.allocation().get().requestedResources(), copy.allocation().get().requestedResources());
        assertEquals(node.allocation().get().isRemovable(), copy.allocation().get().isRemovable());
        assertEquals(1, copy.history().events().size());
        assertEquals(clock.instant().truncatedTo(MILLIS), copy.history().event(History.Event.Type.reserved).get().at());
        assertEquals(NodeType.tenant, copy.type());
    }

    @Test
    public void reboot_and_restart_and_type_no_current_values_serialization() {
        String nodeData = 
                "{\n" +
                "   \"type\" : \"tenant\",\n" +
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

        Node node = nodeSerializer.fromJson(Node.State.provisioned, Utf8.toBytes(nodeData));

        assertEquals("large", node.flavor().name());
        assertEquals(1, node.status().reboot().wanted());
        assertEquals(2, node.status().reboot().current());
        assertEquals(3, node.allocation().get().restartGeneration().wanted());
        assertEquals(4, node.allocation().get().restartGeneration().current());
        assertEquals(Arrays.asList(History.Event.Type.provisioned, History.Event.Type.reserved),
                node.history().events().stream().map(History.Event::type).collect(Collectors.toList()));
        assertTrue(node.allocation().get().isRemovable());
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
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(2, copy.history().events().size());
        assertEquals(clock.instant().truncatedTo(MILLIS), copy.history().event(History.Event.Type.retired).get().at());
        assertEquals(Agent.application,
                     (copy.history().event(History.Event.Type.retired).get()).agent());
        assertTrue(copy.allocation().get().membership().retired());

        Node removable = copy.with(node.allocation().get().removable(true));
        Node removableCopy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(removable));
        assertTrue(removableCopy.allocation().get().isRemovable());
    }

    @Test
    public void assimilated_node_deserialization() {
        Node node = nodeSerializer.fromJson(Node.State.active, ("{\n" +
                "  \"type\": \"tenant\",\n" +
                "  \"hostname\": \"assimilate2.vespahosted.yahoo.tld\",\n" +
                "  \"ipAddresses\": [\"127.0.0.1\"],\n" +
                "  \"openStackId\": \"\",\n" +
                "  \"flavor\": \"ugccloud-container\",\n" +
                "  \"instance\": {\n" +
                "    \"tenantId\": \"by_mortent\",\n" +
                "    \"applicationId\": \"ugc-assimilate\",\n" +
                "    \"instanceId\": \"default\",\n" +
                "    \"serviceId\": \"container/ugccloud-container/0/0\",\n" +
                "    \"restartGeneration\": 0,\n" +
                "    \"wantedVespaVersion\": \"6.42.2\"\n" +
                "  }\n" +
                "}\n").getBytes());
        assertEquals(0, node.history().events().size());
        assertTrue(node.allocation().isPresent());
        assertEquals("ugccloud-container", node.allocation().get().membership().cluster().id().value());
        assertEquals("container", node.allocation().get().membership().cluster().type().name());
        assertEquals(0, node.allocation().get().membership().cluster().group().get().index());
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
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
        Node copy2 = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));

        assertEquals(0, copy2.status().failCount());
    }

    @Test
    public void serialize_parentHostname() {
        final String parentHostname = "parent.yahoo.com";
        Node node = Node.create("myId", new IP.Config(Set.of("127.0.0.1"), Set.of()), "myHostname", nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant)
                .parentHostname(parentHostname)
                .build();

        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(node));
        assertEquals(parentHostname, deserializedNode.parentHostname().get());
    }


    @Test
    public void serializes_multiple_ip_addresses() {
        byte[] nodeWithMultipleIps = createNodeJson("node4.yahoo.tld", "127.0.0.4", "::4");
        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeWithMultipleIps);
        assertEquals(ImmutableSet.of("127.0.0.4", "::4"), deserializedNode.ipConfig().primary());
    }

    @Test
    public void serialize_ip_address_pool() {
        Node node = createNode();

        // Test round-trip with address pool
        node = node.with(node.ipConfig().withPool(IP.Pool.of(
                Set.of("::1", "::2", "::3"),
                List.of(new Address("a"), new Address("b"), new Address("c")))));
        Node copy = nodeSerializer.fromJson(node.state(), nodeSerializer.toJson(node));
        assertEquals(node.ipConfig().pool().getIpSet(), copy.ipConfig().pool().getIpSet());
        assertEquals(Set.copyOf(node.ipConfig().pool().getAddressList()), Set.copyOf(copy.ipConfig().pool().getAddressList()));

        // Test round-trip without address pool (handle empty pool)
        node = createNode();
        copy = nodeSerializer.fromJson(node.state(), nodeSerializer.toJson(node));
        assertEquals(node.ipConfig().pool().getIpSet(), copy.ipConfig().pool().getIpSet());
        assertEquals(Set.copyOf(node.ipConfig().pool().getAddressList()), Set.copyOf(copy.ipConfig().pool().getAddressList()));
    }

    @Test
    public void want_to_retire_defaults_to_false() {
        String nodeData =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"]\n" +
                        "}";
        Node node = nodeSerializer.fromJson(State.provisioned, Utf8.toBytes(nodeData));
        assertFalse(node.status().wantToRetire());
    }

    @Test
    public void flavor_overrides_serialization() {
        Node node = createNode();
        assertEquals(20, node.flavor().resources().diskGb(), 0);
        node = node.with(node.flavor().with(FlavorOverrides.ofDisk(1234)));
        assertEquals(1234, node.flavor().resources().diskGb(), 0);

        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(1234, copy.flavor().resources().diskGb(), 0);
        assertEquals(node, copy);
    }

    @Test
    public void want_to_deprovision_defaults_to_false() {
        String nodeData =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"]\n" +
                        "}";
        Node node = nodeSerializer.fromJson(State.provisioned, Utf8.toBytes(nodeData));
        assertFalse(node.status().wantToDeprovision());
    }

    @Test
    public void vespa_version_serialization() {
        String nodeWithWantedVespaVersion =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"],\n" +
                        "   \"instance\": {\n" +
                        "     \"serviceId\": \"content/myId/0/0/stateful\",\n" +
                        "     \"wantedVespaVersion\": \"6.42.2\"\n" +
                        "   }\n" +
                        "}";
        Node node = nodeSerializer.fromJson(State.active, Utf8.toBytes(nodeWithWantedVespaVersion));
        assertEquals("6.42.2", node.allocation().get().membership().cluster().vespaVersion().toString());
    }

    @Test
    public void os_version_serialization() {
        Node serialized = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(createNode()));
        assertFalse(serialized.status().osVersion().current().isPresent());

        // Update OS version
        serialized = serialized.withCurrentOsVersion(Version.fromString("7.1"), Instant.ofEpochMilli(123))
                               // Another update for same version:
                               .withCurrentOsVersion(Version.fromString("7.1"), Instant.ofEpochMilli(456));
        serialized = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(serialized));
        assertEquals(Version.fromString("7.1"), serialized.status().osVersion().current().get());
        var osUpgradedEvents = serialized.history().events().stream()
                                         .filter(event -> event.type() == History.Event.Type.osUpgraded)
                                         .collect(Collectors.toList());
        assertEquals("OS upgraded event is added", 1, osUpgradedEvents.size());
        assertEquals("Duplicate updates of same version uses earliest instant", Instant.ofEpochMilli(123),
                     osUpgradedEvents.get(0).at());
    }

    @Test
    public void firmware_check_serialization() {
        Node node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(createNode()));
        assertFalse(node.status().firmwareVerifiedAt().isPresent());

        node = node.withFirmwareVerifiedAt(Instant.ofEpochMilli(100));
        node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(node));
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
        Node node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(createNode()));
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
        node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(node));

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
        Node node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(createNode()));
        assertFalse(node.modelName().isPresent());

        node = node.withModelName("some model");
        node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(node));
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
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertTrue(copy.allocation().isPresent());
        assertTrue(copy.allocation().get().networkPorts().isPresent());
        NetworkPorts portsCopy = node.allocation().get().networkPorts().get();
        Collection<NetworkPorts.Allocation> listCopy = portsCopy.allocations();
        assertEquals(list, listCopy);
    }

    @Test
    public void switch_hostname_serialization() {
        Node node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(createNode()));
        assertFalse(node.switchHostname().isPresent());
        String switchHostname = "switch0.example.com";
        node = node.withSwitchHostname(switchHostname);
        node = nodeSerializer.fromJson(State.active, nodeSerializer.toJson(node));
        assertEquals(switchHostname, node.switchHostname().get());
    }

    @Test
    public void exclusive_to_serialization() {
        Node.Builder builder = Node.create("myId", IP.Config.EMPTY, "myHostname",
                nodeFlavors.getFlavorOrThrow("default"), NodeType.host);
        Node node = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(builder.build()));
        assertFalse(node.exclusiveTo().isPresent());

        ApplicationId exclusiveTo = ApplicationId.from("tenant1", "app1", "instance1");
        node = builder.exclusiveTo(exclusiveTo).build();
        node = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(node));
        assertEquals(exclusiveTo, node.exclusiveTo().get());
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
                "\"flavor\":\"default\",\"rebootGeneration\":0," +
                "\"currentRebootGeneration\":0,\"failCount\":0,\"history\":[],\"type\":\"tenant\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private Node createNode() {
        return Node.create("myId",
                           new IP.Config(Set.of("127.0.0.1"), Set.of()),
                           "myHostname",
                           nodeFlavors.getFlavorOrThrow("default"),
                           NodeType.tenant).build();
    }

}
