// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeRebooterTest {

    @Test
    public void testRebootScheduling() {
        Duration rebootInterval = Duration.ofDays(30);
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        ProvisioningTester tester = createTester(rebootInterval, flagSource);

        makeReadyHosts(15, tester);
        NodeRepository nodeRepository = tester.nodeRepository();
        NodeRebooter rebooter = new NodeRebooter(nodeRepository, flagSource, new TestMetric());

        assertReadyHosts(15, nodeRepository, 0L);

        // No reboots within 0x-1x reboot interval
        tester.clock().advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(nodeRepository);
        assertReadyHosts(15, nodeRepository, 0L);

        // All nodes/hosts reboots within 1x-2x reboot interval
        tester.clock().advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(nodeRepository);
        assertReadyHosts(15, nodeRepository, 1L);

        // OS upgrade just before reboots would have been scheduled again
        tester.clock().advance(rebootInterval);
        scheduleOsUpgrade(nodeRepository);
        simulateOsUpgrade(nodeRepository);
        rebooter.maintain();
        simulateReboot(nodeRepository);
        assertReadyHosts(15, nodeRepository, 1L);

        // OS upgrade counts as reboot, so within 0x-1x there is no reboots
        tester.clock().advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(nodeRepository);
        assertReadyHosts(15, nodeRepository, 1L);

        // OS upgrade counts as reboot, but within 1x-2x reboots are scheduled again
        tester.clock().advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(nodeRepository);
        assertReadyHosts(15, nodeRepository, 2L);
    }

    @Test(timeout = 30_000) // Avoid looping forever if assertions don't hold
    public void testRebootScheduledEvenWithSmallProbability() {
        Duration rebootInterval = Duration.ofDays(30);
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        ProvisioningTester tester = createTester(rebootInterval, flagSource);

        makeReadyHosts(2, tester);
        NodeRepository nodeRepository = tester.nodeRepository();
        NodeRebooter rebooter = new NodeRebooter(nodeRepository, flagSource, new TestMetric());

        assertReadyHosts(2, nodeRepository, 0L);

        // No reboots within 0x-1x reboot interval
        tester.clock().advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(nodeRepository);
        assertReadyHosts(2, nodeRepository, 0L);

        // Advancing just a little bit into the 1x-2x interval, there is a >0 probability of
        // rebooting a host. Run until all have been scheduled.
        tester.clock().advance(Duration.ofMinutes(25));
        while (true) {
            rebooter.maintain();
            simulateReboot(nodeRepository);
            NodeList nodes = nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.host);
            int count = withCurrentRebootGeneration(1L, nodes.asList()).size();
            if (count == 2) {
                break;
            }
        }
    }

    private void assertReadyHosts(int expectedCount, NodeRepository nodeRepository, long generation) {
        NodeList nodes = nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.host);
        assertEquals(expectedCount, withCurrentRebootGeneration(generation, nodes.asList()).size());
    }

    private void makeReadyHosts(int count, ProvisioningTester tester) {
        tester.makeReadyNodes(count, new NodeResources(64, 256, 1000, 10), NodeType.host, 10);
    }

    /** Set current reboot generation to the wanted reboot generation whenever it is larger (i.e record a reboot) */
    private void simulateReboot(NodeRepository nodeRepository) {
        for (Node node : nodeRepository.nodes().list(Node.State.ready, Node.State.active)) {
            if (node.status().reboot().wanted() > node.status().reboot().current())
                nodeRepository.nodes().write(node.withCurrentRebootGeneration(node.status().reboot().wanted(),
                                                                              nodeRepository.clock().instant()), () -> {});
        }
    }

    /** Schedule OS upgrade for all host nodes */
    private void scheduleOsUpgrade(NodeRepository nodeRepository) {
        nodeRepository.osVersions().setTarget(NodeType.host, Version.fromString("7.0"), Optional.empty(), false);
    }

    /** Simulate completion of an OS upgrade */
    private void simulateOsUpgrade(NodeRepository nodeRepository) {
        var wantedOsVersion = nodeRepository.osVersions().targetFor(NodeType.host);
        if (wantedOsVersion.isEmpty()) return;
        for (Node node : nodeRepository.nodes().list(Node.State.ready, Node.State.active)) {
            if (wantedOsVersion.get().isAfter(node.status().osVersion().current().orElse(Version.emptyVersion)))
                nodeRepository.nodes().write(node.withCurrentOsVersion(wantedOsVersion.get(), nodeRepository.clock().instant()),
                                             () -> {});
        }
    }

    /** Returns the subset of the given nodes which have the given current reboot generation */
    private List<Node> withCurrentRebootGeneration(long generation, List<Node> nodes) {
        return nodes.stream().filter(n -> n.status().reboot().current() == generation).collect(Collectors.toList());
    }

    private static ProvisioningTester createTester(Duration rebootInterval, InMemoryFlagSource flagSource) {
        flagSource = flagSource.withIntFlag(PermanentFlags.REBOOT_INTERVAL_IN_DAYS.id(), (int) rebootInterval.toDays());
        ProvisioningTester tester = new ProvisioningTester.Builder().flagSource(flagSource).build();
        tester.clock().setInstant(Instant.ofEpochMilli(1605522619000L)); // Use a fixed random seed
        ((MockCurator) tester.getCurator()).setZooKeeperEnsembleConnectionSpec("zk1.host:1,zk2.host:2,zk3.host:3");
        return tester;
    }

}
