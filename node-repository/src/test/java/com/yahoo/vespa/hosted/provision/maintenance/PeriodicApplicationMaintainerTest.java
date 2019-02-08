// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
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
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class PeriodicApplicationMaintainerTest {

    private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");

    private NodeRepository nodeRepository;
    private Fixture fixture;
    private ManualClock clock;

    @Before
    public void before() {
        Curator curator = new MockCurator();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        this.clock = new ManualClock();
        this.nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                                                 new MockNameResolver().mockAnyLookup(),
                                                 new DockerImage("docker-registry.domain.tld:8080/dist/vespa"),
                                                 true);
        this.fixture = new Fixture(zone, nodeRepository, nodeFlavors, curator);

        createReadyNodes(15, nodeRepository, nodeFlavors);
        createHostNodes(2, nodeRepository, nodeFlavors);
    }

    @After
    public void after() {
        this.fixture.maintainer.deconstruct();
    }

    @Test(timeout = 60_000)
    public void test_application_maintenance() {
        // Create applications
        fixture.activate();

        // Exhaust initial wait period
        clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));

        // Fail and park some nodes
        nodeRepository.fail(nodeRepository.getNodes(fixture.app1).get(3).hostname(), Agent.system, "Failing to unit test");
        nodeRepository.fail(nodeRepository.getNodes(fixture.app2).get(0).hostname(), Agent.system, "Failing to unit test");
        nodeRepository.park(nodeRepository.getNodes(fixture.app2).get(4).hostname(), Agent.system, "Parking to unit test");
        int failedInApp1 = 1;
        int failedOrParkedInApp2 = 2;
        assertEquals(fixture.wantedNodesApp1 - failedInApp1, nodeRepository.getNodes(fixture.app1, Node.State.active).size());
        assertEquals(fixture.wantedNodesApp2 - failedOrParkedInApp2, nodeRepository.getNodes(fixture.app2, Node.State.active).size());
        assertEquals(failedInApp1 + failedOrParkedInApp2, nodeRepository.getNodes(NodeType.tenant, Node.State.failed, Node.State.parked).size());
        assertEquals(3, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(2, nodeRepository.getNodes(NodeType.host, Node.State.ready).size());

        // Cause maintenance deployment which will allocate replacement nodes
        fixture.runApplicationMaintainer();
        assertEquals(fixture.wantedNodesApp1, nodeRepository.getNodes(fixture.app1, Node.State.active).size());
        assertEquals(fixture.wantedNodesApp2, nodeRepository.getNodes(fixture.app2, Node.State.active).size());
        assertEquals(0, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());

        // Reactivate the previously failed nodes
        nodeRepository.reactivate(nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname(), Agent.system, getClass().getSimpleName());
        nodeRepository.reactivate(nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname(), Agent.system, getClass().getSimpleName());
        nodeRepository.reactivate(nodeRepository.getNodes(NodeType.tenant, Node.State.parked).get(0).hostname(), Agent.system, getClass().getSimpleName());
        int reactivatedInApp1 = 1;
        int reactivatedInApp2 = 2;
        assertEquals(0, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(fixture.wantedNodesApp1 + reactivatedInApp1, nodeRepository.getNodes(fixture.app1, Node.State.active).size());
        assertEquals(fixture.wantedNodesApp2 + reactivatedInApp2, nodeRepository.getNodes(fixture.app2, Node.State.active).size());
        assertEquals("The reactivated nodes are now active but not part of the application",
                     0, fixture.getNodes(Node.State.active).retired().size());

        // Cause maintenance deployment which will update the applications with the re-activated nodes
        clock.advance(Duration.ofMinutes(35)); // Otherwise redeploys are inhibited
        fixture.runApplicationMaintainer();
        assertEquals("Superflous content nodes are retired",
                     reactivatedInApp2, fixture.getNodes(Node.State.active).retired().size());
        assertEquals("Superflous container nodes are deactivated (this makes little point for container nodes)",
                     reactivatedInApp1, fixture.getNodes(Node.State.inactive).size());
    }

    @Test(timeout = 60_000)
    public void deleted_application_is_not_reactivated() {
        // Create applications
        fixture.activate();

        // Freeze active nodes to simulate an application being deleted during a maintenance run
        List<Node> frozenActiveNodes = nodeRepository.getNodes(Node.State.active);

        // Remove one application without letting the application maintainer know about it
        fixture.remove(fixture.app2);
        assertEquals(fixture.wantedNodesApp2, nodeRepository.getNodes(fixture.app2, Node.State.inactive).size());

        // Nodes belonging to app2 are inactive after maintenance
        fixture.maintainer.setOverriddenNodesNeedingMaintenance(frozenActiveNodes);
        fixture.runApplicationMaintainer();
        assertEquals("Inactive nodes were incorrectly activated after maintenance", fixture.wantedNodesApp2,
                     nodeRepository.getNodes(fixture.app2, Node.State.inactive).size());
    }

    @Test(timeout = 60_000)
    public void application_deploy_inhibits_redeploy_for_a_while() {
        fixture.activate();

        // Holds off on deployments a while after starting
        fixture.runApplicationMaintainer();
        assertFalse("No deployment expected", fixture.deployer.lastDeployTime(fixture.app1).isPresent());
        assertFalse("No deployment expected", fixture.deployer.lastDeployTime(fixture.app2).isPresent());
        // Exhaust initial wait period
        clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));

        // First deployment of applications
        fixture.runApplicationMaintainer();
        Instant firstDeployTime = clock.instant();
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app1).get());
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app2).get());
        clock.advance(Duration.ofMinutes(5));
        fixture.runApplicationMaintainer();
        // Too soon: Not redeployed:
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app1).get());
        assertEquals(firstDeployTime, fixture.deployer.lastDeployTime(fixture.app2).get());

        clock.advance(Duration.ofMinutes(30));
        fixture.runApplicationMaintainer();
        // Redeployed:
        assertEquals(clock.instant(), fixture.deployer.lastDeployTime(fixture.app1).get());
        assertEquals(clock.instant(), fixture.deployer.lastDeployTime(fixture.app2).get());
    }

    @Test(timeout = 60_000)
    public void queues_all_eligible_applications_for_deployment() throws Exception {
        fixture.activate();

        // Exhaust initial wait period
        clock.advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));

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
        final MockDeployer deployer;

        final ApplicationId app1 = ApplicationId.from(TenantName.from("foo1"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ApplicationId app2 = ApplicationId.from(TenantName.from("foo2"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        final ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false, Collections.emptySet());
        final ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false, Collections.emptySet());
        final int wantedNodesApp1 = 5;
        final int wantedNodesApp2 = 7;

        private final TestablePeriodicApplicationMaintainer maintainer;

        Fixture(Zone zone, NodeRepository nodeRepository, NodeFlavors flavors, Curator curator) {
            this.nodeRepository = nodeRepository;
            this.curator = curator;
            this.provisioner =  new NodeRepositoryProvisioner(nodeRepository, flavors, zone, new MockProvisionServiceProvider(), new InMemoryFlagSource());

            Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();
            apps.put(app1, new MockDeployer.ApplicationContext(app1, clusterApp1,
                                                               Capacity.fromNodeCount(wantedNodesApp1, Optional.of("default"), false, true), 1));
            apps.put(app2, new MockDeployer.ApplicationContext(app2, clusterApp2,
                                                               Capacity.fromNodeCount(wantedNodesApp2, Optional.of("default"), false, true), 1));
            this.deployer = new MockDeployer(provisioner, nodeRepository.clock(), apps);
            this.maintainer = new TestablePeriodicApplicationMaintainer(deployer, nodeRepository, Duration.ofDays(1), // Long duration to prevent scheduled runs during test
                                                                        Duration.ofMinutes(30));
        }

        void activate() {
            activate(app1, clusterApp1, wantedNodesApp1, provisioner);
            activate(app2, clusterApp2, wantedNodesApp2, provisioner);
            assertEquals(wantedNodesApp1, nodeRepository.getNodes(app1, Node.State.active).size());
            assertEquals(wantedNodesApp2, nodeRepository.getNodes(app2, Node.State.active).size());
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

        void runApplicationMaintainer() {
            runApplicationMaintainer(true);
        }

        void runApplicationMaintainer(boolean waitForDeployments) {
            maintainer.run();
            while (waitForDeployments && fixture.maintainer.pendingDeployments() != 0);
        }

        NodeList getNodes(Node.State ... states) {
            return new NodeList(nodeRepository.getNodes(NodeType.tenant, states));
        }

    }
    
    private static class TestablePeriodicApplicationMaintainer extends PeriodicApplicationMaintainer {

        private List<Node> overriddenNodesNeedingMaintenance;

        TestablePeriodicApplicationMaintainer setOverriddenNodesNeedingMaintenance(List<Node> overriddenNodesNeedingMaintenance) {
            this.overriddenNodesNeedingMaintenance = overriddenNodesNeedingMaintenance;
            return this;
        }

        TestablePeriodicApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval,
                                              Duration minTimeBetweenRedeployments) {
            super(deployer, nodeRepository, interval, minTimeBetweenRedeployments, new JobControl(nodeRepository.database()));
        }

        @Override
        protected List<Node> nodesNeedingMaintenance() {
            return overriddenNodesNeedingMaintenance != null
                    ? overriddenNodesNeedingMaintenance
                    : super.nodesNeedingMaintenance();
        }

        @Override
        protected boolean shouldBeDeployedOnThisServer(ApplicationId application) {
            return true;
        }

    }

}
