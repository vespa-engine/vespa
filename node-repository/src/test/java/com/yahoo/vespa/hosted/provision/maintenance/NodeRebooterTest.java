// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeRebooterTest {

    @Test
    public void testRebootScheduling() {
        var rebootInterval = Duration.ofDays(30);
        var flagSource = new InMemoryFlagSource().withIntFlag(Flags.REBOOT_INTERVAL_IN_DAYS.id(), (int) rebootInterval.toDays());
        var tester = new MaintenanceTester();
        tester.createReadyHostNodes(15);
        NodeRebooter rebooter = new NodeRebooter(tester.nodeRepository, tester.clock, flagSource);

        assertReadyHosts(15, tester, 0L);

        // No reboots within 0x-1x reboot interval
        tester.clock.advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(tester);
        assertReadyHosts(15, tester, 0L);

        // All nodes/hosts reboots within 1x-2x reboot interval
        tester.clock.advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(tester);
        assertReadyHosts(15, tester, 1L);

        // OS upgrade just before reboots would have been scheduled again
        tester.clock.advance(rebootInterval);
        scheduleOsUpgrade(tester);
        simulateOsUpgrade(tester);
        rebooter.maintain();
        simulateReboot(tester);
        assertReadyHosts(15, tester, 1L);

        // OS upgrade counts as reboot, so within 0x-1x there is no reboots
        tester.clock.advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(tester);
        assertReadyHosts(15, tester, 1L);

        // OS upgrade counts as reboot, but within 1x-2x reboots are scheduled again
        tester.clock.advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(tester);
        assertReadyHosts(15, tester, 2L);
    }

    @Test
    public void testRebootScheduledEvenWithSmallProbability() {
        Duration rebootInterval = Duration.ofDays(30);
        var flagSource = new InMemoryFlagSource().withIntFlag(Flags.REBOOT_INTERVAL_IN_DAYS.id(), (int) rebootInterval.toDays());
        var tester = new MaintenanceTester();
        tester.createReadyHostNodes(2);
        NodeRebooter rebooter = new NodeRebooter(tester.nodeRepository, tester.clock, flagSource);

        assertReadyHosts(2, tester, 0L);

        // No reboots within 0x-1x reboot interval
        tester.clock.advance(rebootInterval);
        rebooter.maintain();
        simulateReboot(tester);
        assertReadyHosts(2, tester, 0L);

        // Advancing just a little bit into the 1x-2x interval, there is a >0 probability of
        // rebooting a host. Run until all have been scheduled.
        tester.clock.advance(Duration.ofMinutes(25));
        for (int i = 0;; ++i) {
            rebooter.maintain();
            simulateReboot(tester);
            List<Node> nodes = tester.nodeRepository.getNodes(NodeType.host, Node.State.ready);
            int count = withCurrentRebootGeneration(1L, nodes).size();
            if (count == 2) {
                break;
            }
        }
    }

    private void assertReadyHosts(int expectedCount, MaintenanceTester tester, long generation) {
        List<Node> nodes = tester.nodeRepository.getNodes(NodeType.host, Node.State.ready);
        assertEquals(expectedCount, withCurrentRebootGeneration(generation, nodes).size());
    }

    /** Set current reboot generation to the wanted reboot generation whenever it is larger (i.e record a reboot) */
    private void simulateReboot(MaintenanceTester tester) {
        for (Node node : tester.nodeRepository.getNodes(Node.State.ready, Node.State.active)) {
            if (node.status().reboot().wanted() > node.status().reboot().current())
                tester.nodeRepository.write(node.withCurrentRebootGeneration(node.status().reboot().wanted(), 
                                                                             tester.clock.instant()), () -> {});
        }
    }

    /** Schedule OS upgrade for all host nodes */
    private void scheduleOsUpgrade(MaintenanceTester tester) {
        tester.nodeRepository.osVersions().setTarget(NodeType.host, Version.fromString("7.0"), false);
    }

    /** Simulate completion of an OS upgrade */
    private void simulateOsUpgrade(MaintenanceTester tester) {
        var wantedOsVersion = tester.nodeRepository.osVersions().targetFor(NodeType.host);
        if (wantedOsVersion.isEmpty()) return;
        for (Node node : tester.nodeRepository.getNodes(Node.State.ready, Node.State.active)) {
            if (wantedOsVersion.get().isAfter(node.status().osVersion().current().orElse(Version.emptyVersion)))
                tester.nodeRepository.write(node.withCurrentOsVersion(wantedOsVersion.get(), tester.clock.instant()),
                                            () -> {});
        }
    }
    
    /** Returns the subset of the given nodes which have the given current reboot generation */
    private List<Node> withCurrentRebootGeneration(long generation, List<Node> nodes) {
        return nodes.stream().filter(n -> n.status().reboot().current() == generation).collect(Collectors.toList());
    }

}
