// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class OperatorChangeApplicationMaintainerTest {

    @Test
    public void test_application_maintenance() {
        NodeResources hostResources = new NodeResources(64, 128, 2000, 10);
        Fixture fixture = new Fixture();
        ManualClock clock = fixture.tester.clock();
        NodeRepository nodeRepository = fixture.nodeRepository;

        fixture.tester.makeReadyNodes(15, fixture.nodeResources);
        fixture.tester.makeReadyNodes(2, hostResources);
        fixture.tester.makeReadyNodes(2, fixture.nodeResources, NodeType.proxy);

        // Create applications
        fixture.activate();
        assertEquals("Initial applications are deployed", 3, fixture.deployer.redeployments);
        OperatorChangeApplicationMaintainer maintainer = new OperatorChangeApplicationMaintainer(fixture.deployer,
                                                                                                 new TestMetric(),
                                                                                                 nodeRepository,
                                                                                                 Duration.ofMinutes(1));
        
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("No changes -> no redeployments", 3, fixture.deployer.redeployments);

        nodeRepository.nodes().fail(nodeRepository.nodes().list().owner(fixture.app1).asList().get(3).hostname(), Agent.system, "Failing to unit test");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("System change -> no redeployments", 3, fixture.deployer.redeployments);

        clock.advance(Duration.ofSeconds(1));
        nodeRepository.nodes().fail(nodeRepository.nodes().list().owner(fixture.app2).asList().get(4).hostname(), Agent.operator, "Manual node failing");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("Operator change -> redeployment", 4, fixture.deployer.redeployments);

        clock.advance(Duration.ofSeconds(1));
        nodeRepository.nodes().fail(nodeRepository.nodes().list().owner(fixture.app3).asList().get(1).hostname(), Agent.operator, "Manual node failing");
        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("Operator change -> redeployment", 5, fixture.deployer.redeployments);

        clock.advance(Duration.ofMinutes(2));
        maintainer.maintain();
        assertEquals("No further operator changes -> no (new) redeployments", 5, fixture.deployer.redeployments);
    }

    private static class Fixture {

        final ProvisioningTester tester;
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

        Fixture() {
            this.tester = new ProvisioningTester.Builder().build();
            this.nodeRepository = tester.nodeRepository();
            Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                    app1, new MockDeployer.ApplicationContext(app1, clusterApp1, Capacity.from(new ClusterResources(wantedNodesApp1, 1, nodeResources))),
                    app2, new MockDeployer.ApplicationContext(app2, clusterApp2, Capacity.from(new ClusterResources(wantedNodesApp2, 1, nodeResources))),
                    app3, new MockDeployer.ApplicationContext(app3, clusterApp3, Capacity.fromRequiredNodeType(NodeType.proxy))) ;
            this.deployer = new MockDeployer(tester.provisioner(), nodeRepository.clock(), apps);
        }

        void activate() {
            deployer.deployFromLocalActive(app1, false).get().activate();
            deployer.deployFromLocalActive(app2, false).get().activate();
            deployer.deployFromLocalActive(app3, false).get().activate();
            assertEquals(wantedNodesApp1, nodeRepository.nodes().list(Node.State.active).owner(app1).size());
            assertEquals(wantedNodesApp2, nodeRepository.nodes().list(Node.State.active).owner(app2).size());
            assertEquals(wantedNodesApp3, nodeRepository.nodes().list(Node.State.active).owner(app3).size());
        }

    }
    
}
