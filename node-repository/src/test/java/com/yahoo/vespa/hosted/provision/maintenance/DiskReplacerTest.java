package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class DiskReplacerTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();
    private final MockHostProvisioner hostProvisioner = new MockHostProvisioner(List.of());
    private final DiskReplacer diskReplacer = new DiskReplacer(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), hostProvisioner);

    @Test
    public void rebuild_host() {
        tester.makeReadyHosts(2, new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)).activateTenantHosts();

        // No rebuilds in initial run
        diskReplacer.maintain();
        assertEquals(0, tester.nodeRepository().nodes().list().rebuilding(true).size());

        // Host starts rebuilding
        tester.nodeRepository().nodes().rebuild("host-1.yahoo.com", true, Agent.RebuildingOsUpgrader,
                tester.nodeRepository().clock().instant());
        diskReplacer.maintain();
        assertEquals(1, tester.nodeRepository().nodes().list().rebuilding(true).size());

        // Rebuild completes
        hostProvisioner.completeRebuildOf("host-1.yahoo.com");
        diskReplacer.maintain();
        assertEquals(0, tester.nodeRepository().nodes().list().rebuilding(true).size());
    }
}
