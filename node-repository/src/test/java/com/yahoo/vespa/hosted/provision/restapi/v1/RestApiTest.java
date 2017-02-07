// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v1;

import com.yahoo.application.container.JDisc;
import com.yahoo.application.Networking;

import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RestApiTest {

    private static final String servicesXml =
                "<jdisc version=\"1.0\">" +
                "    <component id=\"com.yahoo.vespa.hosted.provision.restapi.v1.RestApiTest$MockNodeRepository\"/>" +
                "    <handler id=\"com.yahoo.vespa.hosted.provision.restapi.v1.NodesApiHandler\">" +
                "      <binding>http://*/nodes/v1/</binding>" +
                "    </handler>" +
                "</jdisc>";

    @Test
    public void testTopLevelRequest() throws Exception {
        try (JDisc container = JDisc.fromServicesXml(servicesXml, Networking.disable)) {
            Response response = container.handleRequest(new Request("http://localhost:8080/nodes/v1/"));

            assertEquals("{\"provisioned\":[{\"id\":\"node6\",\"hostname\":\"host6.yahoo.com\",\"flavor\":\"default\"}],\"reserved\":[{\"id\":\"node2\",\"hostname\":\"host2.yahoo.com\",\"flavor\":\"default\",\"owner\":{\"tenant\":\"tenant1\",\"application\":\"application1\",\"instance\":\"instance1\"},\"membership\":{\"clustertype\":\"container\",\"clusterid\":\"id1\",\"index\":1,\"retired\":false},\"restartGeneration\":0},{\"id\":\"node1\",\"hostname\":\"host1.yahoo.com\",\"flavor\":\"default\",\"owner\":{\"tenant\":\"tenant1\",\"application\":\"application1\",\"instance\":\"instance1\"},\"membership\":{\"clustertype\":\"container\",\"clusterid\":\"id1\",\"index\":0,\"retired\":false},\"restartGeneration\":0}],\"active\":[{\"id\":\"node3\",\"hostname\":\"host3.yahoo.com\",\"flavor\":\"default\",\"owner\":{\"tenant\":\"tenant2\",\"application\":\"application2\",\"instance\":\"instance2\"},\"membership\":{\"clustertype\":\"content\",\"clusterid\":\"id2\",\"index\":0,\"retired\":false},\"restartGeneration\":0},{\"id\":\"node4\",\"hostname\":\"host4.yahoo.com\",\"flavor\":\"default\",\"owner\":{\"tenant\":\"tenant2\",\"application\":\"application2\",\"instance\":\"instance2\"},\"membership\":{\"clustertype\":\"content\",\"clusterid\":\"id2\",\"index\":1,\"retired\":false},\"restartGeneration\":0}],\"failed\":[{\"id\":\"node5\",\"hostname\":\"host5.yahoo.com\",\"flavor\":\"default\"}]}",
                         response.getBodyAsString());
        }
    }

    @Test
    public void testSingleNodeRequest() throws Exception {
        try (JDisc container = JDisc.fromServicesXml(servicesXml, Networking.disable)) {
            Response response1 = container.handleRequest(new Request("http://localhost:8080/nodes/v1/?hostname=host1.yahoo.com"));
            assertEquals("{\"reserved\":[{\"id\":\"node1\",\"hostname\":\"host1.yahoo.com\",\"flavor\":\"default\",\"owner\":{\"tenant\":\"tenant1\",\"application\":\"application1\",\"instance\":\"instance1\"},\"membership\":{\"clustertype\":\"container\",\"clusterid\":\"id1\",\"index\":0,\"retired\":false},\"restartGeneration\":0}]}",
                         response1.getBodyAsString());

            Response response2 = container.handleRequest(new Request("http://localhost:8080/nodes/v1/?hostname=host6.yahoo.com"));
            assertEquals("{\"provisioned\":[{\"id\":\"node6\",\"hostname\":\"host6.yahoo.com\",\"flavor\":\"default\"}]}",
                         response2.getBodyAsString());

            Response response3 = container.handleRequest(new Request("http://localhost:8080/nodes/v1/?hostname=nonexisting-host.yahoo.com"));
            assertEquals("{}",
                         response3.getBodyAsString());
        }
    }

    // Instantiated by DI from application package above
    @SuppressWarnings("unused")
    public static class MockNodeRepository extends NodeRepository {

        private static final NodeFlavors flavors = FlavorConfigBuilder.createDummies("default");

        public MockNodeRepository() throws Exception {
            super(flavors, new MockCurator(), Clock.systemUTC(), Zone.defaultZone(),
                    new MockNameResolver().mockAnyLookup());
            populate();
        }

        private void populate() {
            NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(this, flavors, Zone.defaultZone());

            NodeFlavors flavors = FlavorConfigBuilder.createDummies("default");
            List<Node> nodes = new ArrayList<>();
            nodes.add(createNode("node1", "host1.yahoo.com", Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
            nodes.add(createNode("node2", "host2.yahoo.com", Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
            nodes.add(createNode("node3", "host3.yahoo.com", Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
            nodes.add(createNode("node4", "host4.yahoo.com", Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
            nodes.add(createNode("node5", "host5.yahoo.com", Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
            nodes.add(createNode("node6", "host6.yahoo.com", Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
            nodes = addNodes(nodes);
            nodes.remove(5);
            nodes = setDirty(nodes);
            setReady(nodes);
            fail("host5.yahoo.com");

            ApplicationId app1 = ApplicationId.from(TenantName.from("tenant1"), ApplicationName.from("application1"), InstanceName.from("instance1"));
            ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("id1"), Optional.empty());
            provisioner.prepare(app1, cluster1, Capacity.fromNodeCount(2), 1, null);

            ApplicationId app2 = ApplicationId.from(TenantName.from("tenant2"), ApplicationName.from("application2"), InstanceName.from("instance2"));
            ClusterSpec cluster2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id2"), Optional.empty());
            List<HostSpec> hosts = provisioner.prepare(app2, cluster2, Capacity.fromNodeCount(2), 1, null);
            NestedTransaction transaction = new NestedTransaction();
            provisioner.activate(transaction, app2, hosts);
            transaction.commit();
        }

    }

}
