package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class HostRepairMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final HostRepairMaintainer maintainer = new HostRepairMaintainer(tester.controller(), Duration.ofHours(12));

    @Test
    public void maintain() {
        var zoneId = ZoneId.from("dev.us-east-1");
        var hostname1 = HostName.from("node-1-tenant-host-dev.us-east-1");
        var hostname2 = HostName.from("node-2-tenant-host-dev.us-east-1");
        var timestamp = Instant.now().toEpochMilli();

        var node1 = new Node.Builder()
                .state(Node.State.active)
                .hostname(hostname1)
                .build();
        var node2 = new Node.Builder()
                .state(Node.State.breakfixed)
                .hostname(hostname2)
                .build();
        tester.configServer().nodeRepository().putNodes(zoneId, List.of(node1, node2));
        maintainer.maintain();
        var updatedNodes = tester.serviceRegistry().hostRepairClient().getUpdatedNodes();
        assertEquals(1, updatedNodes.size());
        assertEquals(hostname2, updatedNodes.get(0).hostname());
    }
}