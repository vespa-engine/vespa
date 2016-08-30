// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Optional;

/**
 * @author bratseth
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
                             ClusterMembership.from("content/myId/0/0", Optional.empty()),
                             clock.instant());
        assertEquals(1, node.history().events().size());
        node = node.setRestart(new Generation(1, 2));
        node = node.setReboot(new Generation(3, 4));
        node = node.setFlavor(FlavorConfigBuilder.createDummies("large").getFlavorOrThrow("large"));
        node = node.setStatus(node.status().setVespaVersion(Version.fromString("1.2.3")));
        node = node.setStatus(node.status().increaseFailCount().increaseFailCount());
        node = node.setStatus(node.status().setHardwareFailure(Optional.of(Status.HardwareFailureType.mce)));
        node = node.setType(Node.Type.tenant);
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));

        assertEquals(node.id(), copy.id());
        assertEquals(node.hostname(), copy.hostname());
        assertEquals(node.state(), copy.state());
        assertEquals(1, copy.allocation().get().restartGeneration().wanted());
        assertEquals(2, copy.allocation().get().restartGeneration().current());
        assertEquals(3, copy.status().reboot().wanted());
        assertEquals(4, copy.status().reboot().current());
        assertEquals("large", copy.configuration().flavor().name());
        assertEquals("1.2.3", copy.status().vespaVersion().get().toString());
        assertEquals(2, copy.status().failCount());
        assertEquals(Status.HardwareFailureType.mce, copy.status().hardwareFailure().get());
        assertEquals(node.allocation().get().owner(), copy.allocation().get().owner());
        assertEquals(node.allocation().get().membership(), copy.allocation().get().membership());
        assertEquals(node.allocation().get().removable(), copy.allocation().get().removable());
        assertEquals(1, copy.history().events().size());
        assertEquals(clock.instant(), copy.history().event(History.Event.Type.reserved).get().at());
        assertEquals(Node.Type.tenant, copy.type());
    }

    @Test
    public void testDefaultType() {
        Node node = createNode().allocate(ApplicationId.from(TenantName.from("myTenant"),
                ApplicationName.from("myApplication"),
                InstanceName.from("myInstance")),
                ClusterMembership.from("content/myId/0/0", Optional.empty()),
                clock.instant());
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(Node.Type.host, copy.type());
    }

    @Test
    public void testRebootAndRestartandTypeNoCurrentValuesSerialization() {
        String nodeData = 
                "{\n" +
                "   \"type\" : \"tenant\",\n" +
                "   \"rebootGeneration\" : 0,\n" +
                "   \"configuration\" : {\n" +
                "      \"flavor\" : \"default\"\n" +
                "   },\n" +
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
                "      \"restartGeneration\" : 0,\n" +
                "      \"removable\" : false\n" +
                "   },\n" +
                "   \"openStackId\" : \"myId\",\n" +
                "   \"hostname\" : \"myHostname\"\n" +
                "}";

        Node node = nodeSerializer.fromJson(Node.State.provisioned, Utf8.toBytes(nodeData));

        assertEquals(0, node.status().reboot().wanted());
        assertEquals(0, node.status().reboot().current());
        assertEquals(0, node.allocation().get().restartGeneration().wanted());
        assertEquals(0, node.allocation().get().restartGeneration().current());
        assertEquals(Node.Type.tenant, node.type());
    }

    // TODO: Remove when 6.28 is deployed everywhere
    @Test
    public void testLegacyHardwareFailureBooleanDeserialization() {
        String nodeData =
                "{\n" +
                "   \"type\" : \"tenant\",\n" +
                "   \"rebootGeneration\" : 0,\n" +
                "   \"configuration\" : {\n" +
                "      \"flavor\" : \"default\"\n" +
                "   },\n" +
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
                "      \"restartGeneration\" : 0,\n" +
                "      \"removable\" : false\n" +
                "   },\n" +
                "   \"openStackId\" : \"myId\",\n" +
                "   \"hostname\" : \"myHostname\",\n" +
                "   \"hardwareFailure\" : true\n" +
                "}";

        Node node = nodeSerializer.fromJson(Node.State.provisioned, Utf8.toBytes(nodeData));
        assertEquals(Status.HardwareFailureType.unknown, node.status().hardwareFailure().get());
    }
    @Test
    public void testRetiredNodeSerialization() {
        Node node = createNode();

        clock.advance(Duration.ofMinutes(3));
        assertEquals(0, node.history().events().size());
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                                                ApplicationName.from("myApplication"),
                                                InstanceName.from("myInstance")),
                             ClusterMembership.from("content/myId/0", Optional.empty()),
                             clock.instant());
        assertEquals(1, node.history().events().size());
        clock.advance(Duration.ofMinutes(2));
        node = node.retireByApplication(clock.instant());
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(2, copy.history().events().size());
        assertEquals(clock.instant(), copy.history().event(History.Event.Type.retired).get().at());
        assertEquals(History.RetiredEvent.Agent.application,
                     ((History.RetiredEvent) copy.history().event(History.Event.Type.retired).get()).agent());
        assertTrue(copy.allocation().get().membership().retired());

        Node removable = copy.setAllocation(node.allocation().get().makeRemovable());
        Node removableCopy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(removable));
        assertTrue(removableCopy.allocation().get().removable());
    }

    @Test
    public void testAssimilatedDeserialization() {
        Node node = nodeSerializer.fromJson(Node.State.active, "{\"type\":\"tenant\",\"hostname\":\"assimilate2.vespahosted.corp.bf1.yahoo.com\",\"openStackId\":\"\",\"configuration\":{\"flavor\":\"ugccloud-container\"},\"instance\":{\"tenantId\":\"by_mortent\",\"applicationId\":\"ugc-assimilate\",\"instanceId\":\"default\",\"serviceId\":\"container/ugccloud-container/0/0\",\"restartGeneration\":0}}\n".getBytes());
        assertEquals(0, node.history().events().size());
        assertTrue(node.allocation().isPresent());
        assertEquals("ugccloud-container", node.allocation().get().membership().cluster().id().value());
        assertEquals("container", node.allocation().get().membership().cluster().type().name());
        assertEquals("0", node.allocation().get().membership().cluster().group().get().value());
        Node copy = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));
        assertEquals(0, copy.history().events().size());
    }

    @Test
    public void testSetFailCount() {
        Node node = createNode();
        node = node.allocate(ApplicationId.from(TenantName.from("myTenant"),
                        ApplicationName.from("myApplication"),
                        InstanceName.from("myInstance")),
                ClusterMembership.from("content/myId/0/0", Optional.empty()),
                clock.instant());

        node = node.setStatus(node.status().setFailCount(0));
        Node copy2 = nodeSerializer.fromJson(Node.State.provisioned, nodeSerializer.toJson(node));

        assertEquals(0, copy2.status().failCount());
    }

    @Test
    public void serialize_docker_image() {
        Node node = createNode();

        Optional<String> dockerImage = Optional.of("my-docker-image");
        ClusterMembership clusterMembership = ClusterMembership.from("content/myId/0", dockerImage);

        Node nodeWithAllocation = node.setAllocation(
                new Allocation(
                        ApplicationId.from(TenantName.from("myTenant"),
                                ApplicationName.from("myApplication"),
                                InstanceName.from("myInstance")),
                        clusterMembership,
                        new Generation(0, 0),
                        false));

        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(nodeWithAllocation));
        assertEquals(dockerImage, deserializedNode.allocation().get().membership().cluster().dockerImage());
    }

    @Test
    public void serialize_parentHostname() {
        final String parentHostname = "parent.yahoo.com";
        Node node = Node.create("myId", "myHostname", Optional.of(parentHostname), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant);

        Node deserializedNode = nodeSerializer.fromJson(State.provisioned, nodeSerializer.toJson(node));
        assertEquals(parentHostname, deserializedNode.parentHostname().get());
    }

    private Node createNode() {
        return Node.create("myId", "myHostname", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.host);
    }

}
