package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.repair.HostRepairClient;
import com.yahoo.vespa.hosted.controller.api.integration.repair.MockRepairClient;
import com.yahoo.vespa.hosted.controller.api.integration.repair.RepairTicketReport;
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
        var openTicket = new RepairTicketReport("OPEN", "ticket-1", timestamp, timestamp);
        var closedTicket = new RepairTicketReport("CLOSED", "ticket-2", timestamp, timestamp);

        tester.configServer().nodeRepository().addReport(
                zoneId,
                hostname1,
                RepairTicketReport.getReportId(),
                openTicket.toJsonNode());
        tester.configServer().nodeRepository().addReport(
                zoneId,
                hostname2,
                RepairTicketReport.getReportId(),
                closedTicket.toJsonNode());

        maintainer.maintain();
        var updatedNodes = tester.serviceRegistry().hostRepairClient().getUpdatedNodes();
        assertEquals(1, updatedNodes.size());
        assertEquals(hostname1, updatedNodes.get(0).hostname());
    }
}