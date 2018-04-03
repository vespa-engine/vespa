// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class OperatorChangeApplicationMaintainerTest {

    private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");

    private NodeRepository nodeRepository;
    private Fixture fixture;

    @Test
    public void test_application_maintenance() throws InterruptedException {
        ManualClock clock = new ManualClock();
        Curator curator = new MockCurator();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        this.nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                                                 new MockNameResolver().mockAnyLookup(),
                                                 new DockerImage("docker-registry.domain.tld:8080/dist/vespa"),
                                                 true);
        this.fixture = new Fixture(zone, nodeRepository, nodeFlavors, curator);

        createReadyNodes(15, nodeRepository, nodeFlavors);
        createHostNodes(2, nodeRepository, nodeFlavors);

        // Create applications
        fixture.activate();
        OperatorChangeApplicationMaintainer maintainer = new OperatorChangeApplicationMaintainer(fixture.deployer, nodeRepository, clock, Duration.ofMinutes(1), new JobControl(nodeRepository.database()));
        
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("No changes -> no redeployments", 0, fixture.deployer.redeployments);

        nodeRepository.fail(nodeRepository.getNodes(fixture.app1).get(3).hostname(), Agent.system, "Failing to unit test");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("System change -> no redeployments", 0, fixture.deployer.redeployments);

        clock.advance(Duration.ofSeconds(1));
        nodeRepository.fail(nodeRepository.getNodes(fixture.app2).get(4).hostname(), Agent.operator, "Manual node failing");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("Operator change -> redeployment", 1, fixture.deployer.redeployments);

        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("No further operator changes -> no (new) redeployments", 1, fixture.deployer.redeployments);
    }

    private void createReadyNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "host" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private void createHostNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("hostNode" + i, "realHost" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private class Fixture {

        final NodeRepository nodeRepository;
        final NodeRepositoryProvisioner provisioner;
        final Curator curator;

        final ApplicationId app1 = ApplicationId.from(TenantName.from("foo1"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ApplicationId app2 = ApplicationId.from(TenantName.from("foo2"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false);
        final ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false);
        final int wantedNodesApp1 = 5;
        final int wantedNodesApp2 = 7;
        MockDeployer deployer; // created on activation

        Fixture(Zone zone, NodeRepository nodeRepository, NodeFlavors flavors, Curator curator) {
            this.nodeRepository = nodeRepository;
            this.curator = curator;
            this.provisioner =  new NodeRepositoryProvisioner(nodeRepository, flavors, zone);
        }

        void activate() {
            activate(app1, clusterApp1, wantedNodesApp1, provisioner);
            activate(app2, clusterApp2, wantedNodesApp2, provisioner);
            assertEquals(wantedNodesApp1, nodeRepository.getNodes(app1, Node.State.active).size());
            assertEquals(wantedNodesApp2, nodeRepository.getNodes(app2, Node.State.active).size());

            Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();
            apps.put(app1, new MockDeployer.ApplicationContext(app1, clusterApp1,
                                                               Capacity.fromNodeCount(wantedNodesApp1, Optional.of("default"), false), 1));
            apps.put(app2, new MockDeployer.ApplicationContext(app2, clusterApp2,
                                                               Capacity.fromNodeCount(wantedNodesApp2, Optional.of("default"), false), 1));
            this.deployer = new MockDeployer(provisioner, apps);
        }

        private void activate(ApplicationId applicationId, ClusterSpec cluster, int nodeCount, NodeRepositoryProvisioner provisioner) {
            List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(nodeCount), 1, null);
            NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
            provisioner.activate(transaction, applicationId, hosts);
            transaction.commit();
        }

        void remove(ApplicationId application) {
            NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
            provisioner.remove(transaction, application);
            transaction.commit();
        }

        NodeList getNodes(Node.State ... states) {
            return new NodeList(nodeRepository.getNodes(NodeType.tenant, states));
        }

    }
    
}
