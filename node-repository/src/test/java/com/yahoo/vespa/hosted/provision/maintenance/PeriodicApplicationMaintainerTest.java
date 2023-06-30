// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class PeriodicApplicationMaintainerTest {

    private NodeRepository nodeRepository;
    private Fixture fixture;
    private ManualClock clock;

    @Before
    public void before() {
        this.fixture = new Fixture();
        this.nodeRepository = fixture.nodeRepository;
        this.clock = fixture.tester.clock();

        fixture.tester.makeReadyNodes(15, fixture.nodeResources);
        fixture.tester.makeReadyHosts(2, new NodeResources(64, 256, 2000, 10));
    }

    @After
    public void after() {
        this.fixture.maintainer.awaitShutdown();
    }

    @Test(timeout = 60_000)
    public void test_application_maintenance() {
        // Create applications
        fixture.activate();

        // Exhaust initial wait period and set bootstrapping to be done
        clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));
        fixture.setBootstrapping(false);

        // Fail and park some nodes
        nodeRepository.nodes().fail(nodeRepository.nodes().list().owner(fixture.app1).asList().get(3).hostname(), Agent.system, "Failing to unit test");
        nodeRepository.nodes().fail(nodeRepository.nodes().list().owner(fixture.app2).asList().get(0).hostname(), Agent.system, "Failing to unit test");
        nodeRepository.nodes().park(nodeRepository.nodes().list().owner(fixture.app2).asList().get(4).hostname(), false, Agent.system, "Parking to unit test");
        int failedInApp1 = 1;
        int failedOrParkedInApp2 = 2;
        assertEquals(fixture.wantedNodesApp1 - failedInApp1, nodeRepository.nodes().list(Node.State.active).owner(fixture.app1).size());
        assertEquals(fixture.wantedNodesApp2 - failedOrParkedInApp2, nodeRepository.nodes().list(Node.State.active).owner(fixture.app2).size());
        assertEquals(failedInApp1 + failedOrParkedInApp2, nodeRepository.nodes().list(Node.State.failed, Node.State.parked).nodeType(NodeType.tenant).size());
        assertEquals(3, nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(2, nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.host).size());

        // Cause maintenance deployment which will allocate replacement nodes
        fixture.runApplicationMaintainer();
        assertEquals(fixture.wantedNodesApp1, nodeRepository.nodes().list(Node.State.active).owner(fixture.app1).size());
        assertEquals(fixture.wantedNodesApp2, nodeRepository.nodes().list(Node.State.active).owner(fixture.app2).size());
        assertEquals(0, nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());

        // Reactivate the previously failed nodes
        nodeRepository.nodes().reactivate(nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).first().get().hostname(), Agent.system, getClass().getSimpleName());
        nodeRepository.nodes().reactivate(nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).first().get().hostname(), Agent.system, getClass().getSimpleName());
        nodeRepository.nodes().reactivate(nodeRepository.nodes().list(Node.State.parked).nodeType(NodeType.tenant).first().get().hostname(), Agent.system, getClass().getSimpleName());
        int reactivatedInApp1 = 1;
        int reactivatedInApp2 = 2;
        assertEquals(0, nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(fixture.wantedNodesApp1 + reactivatedInApp1, nodeRepository.nodes().list(Node.State.active).owner(fixture.app1).size());
        assertEquals(fixture.wantedNodesApp2 + reactivatedInApp2, nodeRepository.nodes().list(Node.State.active).owner(fixture.app2).size());
        assertEquals("The reactivated nodes are now active but not part of the application",
                     0, fixture.getNodes(Node.State.active).retired().size());

        // Cause maintenance deployment which will update the applications with the re-activated nodes
        clock.advance(Duration.ofMinutes(35)); // Otherwise redeploys are inhibited
        fixture.runApplicationMaintainer();
        assertEquals("Superfluous content nodes are retired",
                     reactivatedInApp2, fixture.getNodes(Node.State.active).retired().size());
        assertEquals("Superfluous container nodes are deallocated",
                     reactivatedInApp1, fixture.getNodes(Node.State.dirty).size());
    }

    @Test(timeout = 60_000)
    public void deleted_application_is_not_reactivated() {
        // Create applications
        fixture.activate();

        // Freeze active nodes to simulate an application being deleted during a maintenance run
        NodeList frozenActiveNodes = nodeRepository.nodes().list(Node.State.active);

        // Remove one application without letting the application maintainer know about it
        fixture.remove(fixture.app2);
        assertEquals(fixture.wantedNodesApp2, nodeRepository.nodes().list(Node.State.inactive).owner(fixture.app2).size());

        // Nodes belonging to app2 are inactive after maintenance
        fixture.maintainer.setOverriddenNodesNeedingMaintenance(frozenActiveNodes);
        fixture.runApplicationMaintainer();
        assertEquals("Inactive nodes were incorrectly activated after maintenance", fixture.wantedNodesApp2,
                     nodeRepository.nodes().list(Node.State.inactive).owner(fixture.app2).size());
    }

    @Test(timeout = 60_000)
    public void application_deploy_inhibits_redeploy_for_a_while() {
        fixture.activate();

        assertEquals("No deployment expected", 2, fixture.deployer.redeployments);

        // Holds off on deployments a while after starting
        fixture.runApplicationMaintainer();
        assertEquals("No deployment expected", 2, fixture.deployer.redeployments);

        // Exhaust initial wait period
        clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));

        // Will not do any deployments, as bootstrapping is still in progress
        fixture.runApplicationMaintainer();
        assertEquals("No deployment expected", 2, fixture.deployer.redeployments);

        // First deployment of applications will happen now, as bootstrapping is done
        fixture.setBootstrapping(false);
        fixture.runApplicationMaintainer();
        assertEquals("No deployment expected", 4, fixture.deployer.redeployments);

        Instant firstDeployTime = clock.instant();
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app1).get());
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app2).get());
        clock.advance(Duration.ofMinutes(5));
        fixture.runApplicationMaintainer();
        // Too soon: Not redeployed:
        assertEquals("No deployment expected", 4, fixture.deployer.redeployments);
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app1).get());
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app2).get());

        clock.advance(Duration.ofMinutes(30));
        fixture.runApplicationMaintainer();
        // Redeployed:
        assertEquals("No deployment expected", 6, fixture.deployer.redeployments);
        assertEquals(clock.instant(), fixture.deployer.lastDeployTime(fixture.app1).get());
        assertEquals(clock.instant(), fixture.deployer.lastDeployTime(fixture.app2).get());
    }

    @Test(timeout = 60_000)
    public void queues_all_eligible_applications_for_deployment() throws Exception {
        fixture.activate();

        // Exhaust initial wait period and set bootstrapping to be done
        clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));
        fixture.setBootstrapping(false);

        // Lock deployer to simulate slow deployments
        fixture.deployer.lock().lockInterruptibly();

        try {
            // Queues all eligible applications
            assertEquals(2, fixture.maintainer.applicationsNeedingMaintenance().size());
            fixture.runApplicationMaintainer(false);
            assertEquals(2, fixture.maintainer.pendingDeployments());

            // Enough time passes to make applications eligible for another periodic deployment
            clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));
            fixture.runApplicationMaintainer(false);

            // Deployments are not re-queued as previous deployments are still pending
            assertEquals(2, fixture.maintainer.pendingDeployments());

            // Slow deployments complete
            fixture.deployer.lock().unlock();
            fixture.runApplicationMaintainer();
            Instant deployTime = clock.instant();
            assertEquals(deployTime, fixture.deployer.lastDeployTime(fixture.app1).get());
            assertEquals(deployTime, fixture.deployer.lastDeployTime(fixture.app2).get());

            // Too soon: Already deployed recently
            clock.advance(Duration.ofMinutes(5));
            assertEquals(0, fixture.maintainer.applicationsNeedingMaintenance().size());
        } finally {
            if (fixture.deployer.lock().isHeldByCurrentThread()) {
                fixture.deployer.lock().unlock();
            }
        }
    }

    private class Fixture {

        final NodeRepository nodeRepository;
        final MockDeployer deployer;
        final ProvisioningTester tester;

        final NodeResources nodeResources = new NodeResources(2, 8, 50, 1);
        final ApplicationId app1 = ApplicationId.from(TenantName.from("foo1"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ApplicationId app2 = ApplicationId.from(TenantName.from("foo2"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        final ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        final int wantedNodesApp1 = 5;
        final int wantedNodesApp2 = 7;

        private final TestablePeriodicApplicationMaintainer maintainer;

        Fixture() {
            this.tester = new ProvisioningTester.Builder().build();
            this.nodeRepository = tester.nodeRepository();
            Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                    app1, new MockDeployer.ApplicationContext(app1, clusterApp1, Capacity.from(new ClusterResources(wantedNodesApp1, 1, nodeResources))),
                    app2, new MockDeployer.ApplicationContext(app2, clusterApp2, Capacity.from(new ClusterResources(wantedNodesApp2, 1, nodeResources))));
            this.deployer = new MockDeployer(tester.provisioner(), nodeRepository.clock(), apps);
            this.maintainer = new TestablePeriodicApplicationMaintainer(deployer, nodeRepository, Duration.ofDays(1), // Long duration to prevent scheduled runs during test
                                                                        Duration.ofMinutes(30));
        }

        void activate() {
            deployer.deployFromLocalActive(app1, false).get().activate();
            deployer.deployFromLocalActive(app2, false).get().activate();
            assertEquals(wantedNodesApp1, nodeRepository.nodes().list(Node.State.active).owner(app1).size());
            assertEquals(wantedNodesApp2, nodeRepository.nodes().list(Node.State.active).owner(app2).size());
        }

        void remove(ApplicationId application) {
            deployer.removeApplication(application);
        }

        void runApplicationMaintainer() {
            runApplicationMaintainer(true);
        }

        void runApplicationMaintainer(boolean waitForDeployments) {
            maintainer.run();
            while (waitForDeployments && fixture.maintainer.pendingDeployments() != 0);
        }

        NodeList getNodes(Node.State ... states) {
            return nodeRepository.nodes().list(states).nodeType(NodeType.tenant);
        }

        void setBootstrapping(boolean bootstrapping) {
            deployer.setBootstrapping(bootstrapping);
        }

    }

    private static class TestablePeriodicApplicationMaintainer extends PeriodicApplicationMaintainer {

        private NodeList overriddenNodesNeedingMaintenance;

        void setOverriddenNodesNeedingMaintenance(NodeList overriddenNodesNeedingMaintenance) {
            this.overriddenNodesNeedingMaintenance = overriddenNodesNeedingMaintenance;
        }

        TestablePeriodicApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval,
                                              Duration minTimeBetweenRedeployments) {
            super(deployer, new TestMetric(), nodeRepository, interval, minTimeBetweenRedeployments, new InMemoryFlagSource());
        }

        @Override
        protected NodeList nodesNeedingMaintenance() {
            return overriddenNodesNeedingMaintenance != null
                   ? overriddenNodesNeedingMaintenance
                   : super.nodesNeedingMaintenance();
        }

    }

}
