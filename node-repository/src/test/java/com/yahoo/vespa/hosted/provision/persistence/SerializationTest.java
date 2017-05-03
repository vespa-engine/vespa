// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 */
public class SerializationTest {

    private final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "large", "ugccloud-container");
    private final NodeSerializer nodeSerializer = new NodeSerializer(nodeFlavors);
    private final ManualClock clock = new ManualClock();

    @Test
    public void testProvisionedNodeSerialization() {
        Node node = createNode();

        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(node.id(), copy.id());
        assertEquals(node.hostname(), copy.hostname());
        assertEquals(node.state(), copy.state());
        assertFalse(copy.allocation().isPresent());
        assertEquals(0, copy.history().events().size());
    }

    @Test
    public void testReservedNodeSerialization() {
        Node node = createNode();

        clock.advance(Duration.ofMinutes(3));
        assertEquals(0, node.history().events().size());
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                                                ApplicationName.from("myApplication"),
                                                InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0/0", Vtag.currentVersion),
                             clock.instant());
        assertEquals(1, node.history().events().size());
        node = node.withRestart(new Generation(1, 2));
        node = node.withReboot(new Generation(3, 4));
        node = node.with(FlavorConfigBuilder.createDummies("large").getFlavorOrThrow("large"));
        node = node.with(node.status().withVespaVersion(Version.fromString("1.2.3")));
        node = node.with(node.status().withIncreasedFailCount().withIncreasedFailCount());
        node = node.with(node.status().withHardwareFailure(Optional.of(Status.HardwareFailureType.memory_mcelog)));
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
        assertEquals(Status.HardwareFailureType.memory_mcelog, copy.status().hardwareFailure().get());
        assertEquals(node.allocation().get().owner(), copy.allocation().get().owner());
        assertEquals(node.allocation().get().membership(), copy.allocation().get().membership());
        assertEquals(node.allocation().get().isRemovable(), copy.allocation().get().isRemovable());
        assertEquals(1, copy.history().events().size());
        assertEquals(clock.instant(), copy.history().event(History.Event.Type.reserved).get().at());
        assertEquals(NodeType.tenant, copy.type());
    }

    @Test
    public void testDefaultType() {
        Node node = createNode().allocate(ApplicationId.from(TenantName.from("myTenant"),
                ApplicationName.from("myApplication"),
                InstanceName.from("myInstance")),
                ClusterMembership.from("content/myId/0/0", Vtag.currentVersion),
                clock.instant());
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(NodeType.host, copy.type());
    }

    @Test
    public void testRebootAndRestartAndTypeNoCurrentValuesSerialization() {
        String nodeData = 
                "{\n" +
                "   \"type\" : \"tenant\",\n" +
                "   \"rebootGeneration\" : 1,\n" +
                "   \"currentRebootGeneration\" : 2,\n" +
                "   \"flavor\" : \"large\",\n" +
                "   \"history\" : [\n" +
                "      {\n" +
                "         \"type\" : \"reserved\",\n" +
                "         \"at\" : 1444391402611\n" +
                "      }\n" +
                "   ],\n" +
                "   \"instance\" : {\n" +
                "      \"applicationId\" : \"myApplication\",\n" +
                "      \"tenantId\" : \"myTenant\",\n" +
                "      \"instanceId\" : \"myInstance\",\n" +
                "      \"serviceId\" : \"content/myId/0\",\n" +
                "      \"restartGeneration\" : 3,\n" +
                "      \"currentRestartGeneration\" : 4,\n" +
                "      \"removable\" : true\n" +
                "   },\n" +
                "   \"openStackId\" : \"myId\",\n" +
                "   \"hostname\" : \"myHostname\",\n" +
                "   \"ipAddresses\" : [\"127.0.0.1\"]\n" +
                "}";

        Node node = nodeSerializer.fromJson(Node.State.provisioned, Utf8.toBytes(nodeData));

        assertEquals("large", node.flavor().canonicalName());
        assertEquals(1, node.status().reboot().wanted());
        assertEquals(2, node.status().reboot().current());
        assertEquals(3, node.allocation().get().restartGeneration().wanted());
        assertEquals(4, node.allocation().get().restartGeneration().current());
        assertTrue(node.allocation().get().isRemovable());
        assertEquals(NodeType.tenant, node.type());
    }

    @Test
    public void testRetiredNodeSerialization() {
        Node node = createNode();

        clock.advance(Duration.ofMinutes(3));
        assertEquals(0, node.history().events().size());
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                                                ApplicationName.from("myApplication"),
                                                InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0", Vtag.currentVersion),
                             clock.instant());
        assertEquals(1, node.history().events().size());
        clock.advance(Duration.ofMinutes(2));
        node = node.retire(Agent.application, clock.instant());
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(2, copy.history().events().size());
        assertEquals(clock.instant(), copy.history().event(History.Event.Type.retired).get().at());
        assertEquals(Agent.application,
                     (copy.history().event(History.Event.Type.retired).get()).agent());
        assertTrue(copy.allocation().get().membership().retired());

        Node removable = copy.with(node.allocation().get().removable());
        Node removableCopy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(removable));
        assertTrue(removableCopy.allocation().get().isRemovable());
    }

    @Test
    public void testAssimilatedDeserialization() {
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
                "    \"restartGeneration\": 0\n" +
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
    public void testSetFailCount() {
        Node node = createNode();
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                             ApplicationName.from("myApplication"),
                             InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0/0", Vtag.currentVersion),
                             clock.instant());

        node = node.with(node.status().setFailCount(0));
        Node copy2 = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));

        assertEquals(0, copy2.status().failCount());
    }

    @Test
    public void serialize_docker_image() {
        Node node = createNode();

        Version version = new DockerImage("docker-registry.ops.yahoo.com:4443/vespa/ci:6.42.0").tagAsVersion();
        ClusterMembership clusterMembership = ClusterMembership.from("content/myId/0", version);

        Node nodeWithAllocation = node.with(
                new Allocation(ApplicationId.from(TenantName.from("myTenant"),
                                                  ApplicationName.from("myApplication"),
                                                  InstanceName.from("myInstance")),
                               clusterMembership,
                               new Generation(0, 0),
                               false));

        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(nodeWithAllocation));
        assertEquals("docker-registry.ops.yahoo.com:4443/vespa/ci:6.42.0", deserializedNode.allocation().get().membership().cluster().dockerImage());
    }

    @Test
    public void serialize_parentHostname() {
        final String parentHostname = "parent.yahoo.com";
        Node node = Node.create("myId", singleton("127.0.0.1"), Collections.emptySet(), "myHostname", Optional.of(parentHostname), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant);

        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(node));
        assertEquals(parentHostname, deserializedNode.parentHostname().get());
    }

    @Test
    public void serializes_multiple_ip_addresses() {
        byte[] nodeWithMultipleIps = createNodeJson("node4.yahoo.tld", "127.0.0.4", "::4");
        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeWithMultipleIps);
        assertEquals(ImmutableSet.of("127.0.0.4", "::4"), deserializedNode.ipAddresses());
    }

    @Test
    public void serialize_additional_ip_addresses() {
        Node node = createNode();

        // Test round-trip with additional addresses
        node = node.withAdditionalIpAddresses(ImmutableSet.of("10.0.0.1", "10.0.0.2", "10.0.0.3"));
        Node copy = nodeSerializer.fromJson(node.state(), nodeSerializer.toJson(node));
        assertEquals(node.additionalIpAddresses(), copy.additionalIpAddresses());

        // Test round-trip without additional addresses (handle empty ip set)
        node = createNode();
        copy = nodeSerializer.fromJson(node.state(), nodeSerializer.toJson(node));
        assertEquals(node.additionalIpAddresses(), copy.additionalIpAddresses());
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
    // TODO: Remove after April 2017
    public void vespa_version_serialization() throws Exception {
        String nodeWithDockerImage =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"],\n" +
                        "   \"instance\": {\n" +
                        "     \"serviceId\": \"content/myId/0\",\n" +
                        "     \"dockerImage\": \"docker-registry.some.domain:4443/vespa/ci:6.42.1\"\n" +
                        "   }\n" +
                        "}";
        Node node = nodeSerializer.fromJson(State.active, Utf8.toBytes(nodeWithDockerImage));
        assertEquals("6.42.1", node.allocation().get().membership().cluster().vespaVersion().toString());
        assertEquals("docker-registry.ops.yahoo.com:4443/vespa/ci:6.42.1", node.allocation().get().membership().cluster().dockerImage());

        String nodeWithWantedVespaVersion =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"],\n" +
                        "   \"instance\": {\n" +
                        "     \"serviceId\": \"content/myId/0\",\n" +
                        "     \"wantedVespaVersion\": \"6.42.2\"\n" +
                        "   }\n" +
                        "}";
        node = nodeSerializer.fromJson(State.active, Utf8.toBytes(nodeWithWantedVespaVersion));
        assertEquals("6.42.2", node.allocation().get().membership().cluster().vespaVersion().toString());
        assertEquals("docker-registry.ops.yahoo.com:4443/vespa/ci:6.42.2", node.allocation().get().membership().cluster().dockerImage());
    }

    @Test
    public void docker_image_is_derived_from_vespa_version() throws Exception {
        String nodeData =
                "{\n" +
                        "   \"type\" : \"tenant\",\n" +
                        "   \"flavor\" : \"large\",\n" +
                        "   \"openStackId\" : \"myId\",\n" +
                        "   \"hostname\" : \"myHostname\",\n" +
                        "   \"ipAddresses\" : [\"127.0.0.1\"],\n" +
                        "   \"vespaVersion\": \"6.42.1\"\n" +
                        "}";
        Node node = nodeSerializer.fromJson(State.active, Utf8.toBytes(nodeData));
        assertEquals("docker-registry.ops.yahoo.com:4443/vespa/ci:6.42.1", node.status().dockerImage().get());
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
        return Node.create("myId", singleton("127.0.0.1"), Collections.emptySet(), "myHostname", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host);
    }

}
