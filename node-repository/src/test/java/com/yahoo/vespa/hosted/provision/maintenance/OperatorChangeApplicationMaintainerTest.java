// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
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
    public void test_application_maintenance() {
        ManualClock clock = new ManualClock();
        Curator curator = new MockCurator();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        this.nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                                                 new MockNameResolver().mockAnyLookup(),
                                                 DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"),
                                                 true);
        this.fixture = new Fixture(zone, nodeRepository);

        createReadyNodes(15, this.fixture.nodeResources, nodeRepository);
        createHostNodes(2, nodeRepository, nodeFlavors);
        createProxyNodes(2, nodeRepository, nodeFlavors);

        // Create applications
        fixture.activate();
        assertEquals("Initial applications are deployed", 3, fixture.deployer.redeployments);
        OperatorChangeApplicationMaintainer maintainer = new OperatorChangeApplicationMaintainer(fixture.deployer, nodeRepository, Duration.ofMinutes(1));
        
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("No changes -> no redeployments", 3, fixture.deployer.redeployments);

        nodeRepository.fail(nodeRepository.getNodes(fixture.app1).get(3).hostname(), Agent.system, "Failing to unit test");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("System change -> no redeployments", 3, fixture.deployer.redeployments);

        clock.advance(Duration.ofSeconds(1));
        nodeRepository.fail(nodeRepository.getNodes(fixture.app2).get(4).hostname(), Agent.operator, "Manual node failing");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("Operator change -> redeployment", 4, fixture.deployer.redeployments);

        clock.advance(Duration.ofSeconds(1));
        nodeRepository.fail(nodeRepository.getNodes(fixture.app3).get(1).hostname(), Agent.operator, "Manual node failing");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("Operator change -> redeployment", 5, fixture.deployer.redeployments);

        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("No further operator changes -> no (new) redeployments", 5, fixture.deployer.redeployments);
    }

    private void createReadyNodes(int count, NodeResources resources, NodeRepository nodeRepository) {
        createReadyNodes(count, new Flavor(resources), nodeRepository);
    }

    private void createReadyNodes(int count, Flavor flavor, NodeRepository nodeRepository) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "host" + i, Optional.empty(), flavor, NodeType.tenant));
        nodes = nodeRepository.addNodes(nodes, Agent.system);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private void createHostNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("hostNode" + i, "realHost" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes, Agent.system);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private void createProxyNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("proxyNode" + i, "proxyHost" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.proxy));
        nodes = nodeRepository.addNodes(nodes, Agent.system);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private static class Fixture {

        final NodeRepository nodeRepository;
        final MockDeployer deployer;

        final NodeResources nodeResources = new NodeResources(2, 8, 50, 1);
        final ApplicationId app1 = ApplicationId.from(TenantName.from("foo1"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ApplicationId app2 = ApplicationId.from(TenantName.from("foo2"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ApplicationId app3 = ApplicationId.from(TenantName.from("vespa-hosted"), ApplicationName.from("routing"), InstanceName.from("default"));
        final ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        final ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        final ClusterSpec clusterApp3 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("routing")).vespaVersion("6.42").build();
        final int wantedNodesApp1 = 5;
        final int wantedNodesApp2 = 7;
        final int wantedNodesApp3 = 2;

        Fixture(Zone zone, NodeRepository nodeRepository) {
            this.nodeRepository = nodeRepository;
            NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository,
                                                                                  zone,
                                                                                  new MockProvisionServiceProvider(),
                                                                                  new InMemoryFlagSource());

            Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                    app1, new MockDeployer.ApplicationContext(app1, clusterApp1, Capacity.fromCount(wantedNodesApp1, nodeResources), 1),
                    app2, new MockDeployer.ApplicationContext(app2, clusterApp2, Capacity.fromCount(wantedNodesApp2, nodeResources), 1),
                    app3, new MockDeployer.ApplicationContext(app3, clusterApp3, Capacity.fromRequiredNodeType(NodeType.proxy), 0)) ;
            this.deployer = new MockDeployer(provisioner, nodeRepository.clock(), apps);
        }

        void activate() {
            deployer.deployFromLocalActive(app1, false).get().activate();
            deployer.deployFromLocalActive(app2, false).get().activate();
            deployer.deployFromLocalActive(app3, false).get().activate();
            assertEquals(wantedNodesApp1, nodeRepository.getNodes(app1, Node.State.active).size());
            assertEquals(wantedNodesApp2, nodeRepository.getNodes(app2, Node.State.active).size());
            assertEquals(wantedNodesApp3, nodeRepository.getNodes(app3, Node.State.active).size());
        }

    }
    
}
