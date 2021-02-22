// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author jonmv
 */
public class DedicatedClusterControllerClusterMigratorTest {

    @Test
    public void testMigration() throws InterruptedException, TimeoutException {
        ApplicationId id1 = ApplicationId.from("t", "a", "i1"), id2 = ApplicationId.from("t", "a", "i2");
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.clock().setInstant(Instant.EPOCH); // EPOCH was a week-day.
        tester.makeReadyNodes(4, new NodeResources(1.5, 8, 50, 0.3));
        tester.makeReadyHosts(1, NodeResources.unspecified());
        tester.deploy(id1, Capacity.from(new ClusterResources(2, 1, NodeResources.unspecified())));
        tester.deploy(id2, Capacity.from(new ClusterResources(2, 1, NodeResources.unspecified())));
        MockDeployer deployer = new MockDeployer();
        InMemoryFlagSource flags = new InMemoryFlagSource();
        AtomicBoolean isQuiescent = new AtomicBoolean();
        OrchestratorMock orchestrator = new OrchestratorMock() {
            @Override public boolean isQuiescent(ApplicationId id) { return isQuiescent.get(); }
        };

        DedicatedClusterControllerClusterMigrator migrator = new DedicatedClusterControllerClusterMigrator(deployer,
                                                                                                           new MockMetric(),
                                                                                                           tester.nodeRepository(),
                                                                                                           Duration.ofDays(365),
                                                                                                           flags,
                                                                                                           orchestrator);
        assertFalse(deployer.getDedicatedClusterControllerCluster(id1));

        // Set all conditions true except time window.
        flags.withBooleanFlag(Flags.DEDICATED_CLUSTER_CONTROLLER_CLUSTER.id(), true);
        isQuiescent.set(true);
        deployer.deployedAt.set(tester.clock().instant().minus(Duration.ofMinutes(15)));
        migrator.maintain();
        assertFalse(deployer.getDedicatedClusterControllerCluster(id1));

        // Enter time window, but no longer quiescent.
        tester.clock().advance(Duration.ofHours(8));
        isQuiescent.set(false);
        migrator.maintain();
        assertFalse(deployer.getDedicatedClusterControllerCluster(id1));

        // Quiescent, but no longer flagged.
        isQuiescent.set(true);
        flags.withBooleanFlag(Flags.DEDICATED_CLUSTER_CONTROLLER_CLUSTER.id(), false);
        migrator.maintain();
        assertFalse(deployer.getDedicatedClusterControllerCluster(id1));

        // Flagged, but recently deployed.
        flags.withBooleanFlag(Flags.DEDICATED_CLUSTER_CONTROLLER_CLUSTER.id(), true);
        deployer.deployedAt.set(tester.clock().instant().minus(Duration.ofMinutes(5)));
        migrator.maintain();
        assertFalse(deployer.getDedicatedClusterControllerCluster(id1));

        // Finally, all stars align.
        deployer.deployedAt.set(tester.clock().instant().minus(Duration.ofMinutes(15)));
        migrator.maintain();
        assertTrue(deployer.getDedicatedClusterControllerCluster(id1));  // Lex sorting, t.a.i1 before t.a.i2.
        assertFalse(deployer.getDedicatedClusterControllerCluster(id2));
        assertEquals(1, deployer.phaser.awaitAdvanceInterruptibly(deployer.phaser.arrive(), 10, TimeUnit.SECONDS));

        migrator.maintain();
        assertTrue(deployer.getDedicatedClusterControllerCluster(id2));
        assertEquals(2, deployer.phaser.awaitAdvanceInterruptibly(deployer.phaser.arrive(), 10, TimeUnit.SECONDS));

        assertEquals(Set.of(), migrator.applicationsNeedingMaintenance());
    }


    private static class MockDeployer implements Deployer {

        final Phaser phaser = new Phaser(2); // Test thread and deployer.
        final Set<ApplicationId> dedicatedCCC = new ConcurrentSkipListSet<>();
        final AtomicReference<Instant> deployedAt = new AtomicReference<>();

        @Override
        public Optional<Deployment> deployFromLocalActive(ApplicationId application, boolean bootstrap) {
            return Optional.of(new Deployment() {
                @Override public void prepare() { fail("Shouldn't run"); }
                @Override public long activate() { return phaser.arriveAndAwaitAdvance(); }
                @Override public void restart(HostFilter filter) { fail("Shouldn't run"); }
            });
        }

        @Override
        public Optional<Deployment> deployFromLocalActive(ApplicationId application, Duration timeout, boolean bootstrap) {
            return deployFromLocalActive(application, bootstrap);
        }

        @Override
        public Optional<Instant> lastDeployTime(ApplicationId application) {
            return Optional.ofNullable(deployedAt.get());
        }

        @Override
        public void setDedicatedClusterControllerCluster(ApplicationId id) {
            dedicatedCCC.add(id);
        }

        @Override
        public boolean getDedicatedClusterControllerCluster(ApplicationId id) {
            return dedicatedCCC.contains(id);
        }

    }

}
