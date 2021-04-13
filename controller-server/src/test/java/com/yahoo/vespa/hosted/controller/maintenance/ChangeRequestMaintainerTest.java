// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.MockChangeRequestClient;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import org.junit.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class ChangeRequestMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final MockChangeRequestClient changeRequestClient = tester.serviceRegistry().changeRequestClient();
    private final ChangeRequestMaintainer changeRequestMaintainer = new ChangeRequestMaintainer(tester.controller(), Duration.ofMinutes(1));

    @Test
    public void only_approve_requests_pending_approval() {
        var changeRequest1 = newChangeRequest("id1", ChangeRequest.Approval.APPROVED);
        var changeRequest2 = newChangeRequest("id2", ChangeRequest.Approval.REQUESTED);
        var upcomingChangeRequests = List.of(
                changeRequest1,
                changeRequest2
        );

        changeRequestClient.setUpcomingChangeRequests(upcomingChangeRequests);
        changeRequestMaintainer.maintain();

        var approvedChangeRequests = changeRequestClient.getApprovedChangeRequests();

        assertEquals(1, approvedChangeRequests.size());
        assertEquals("id2", approvedChangeRequests.get(0).getId());
        var writtenChangeRequests = tester.curator().readChangeRequests();
        assertEquals(2, writtenChangeRequests.size());

        var expectedChangeRequest = new VespaChangeRequest(changeRequest1, ZoneId.from("prod.us-east-3"));
        assertEquals(expectedChangeRequest, writtenChangeRequests.get(0));
    }

    private ChangeRequest newChangeRequest(String id, ChangeRequest.Approval approval) {
        return new ChangeRequest.Builder()
                .id(id)
                .approval(approval)
                .impact(ChangeRequest.Impact.VERY_HIGH)
                .impactedSwitches(List.of())
                .impactedHosts(List.of("node-1-tenant-host-prod.us-east-3"))
                .changeRequestSource(new ChangeRequestSource.Builder()
                        .plannedStartTime(ZonedDateTime.now())
                        .plannedEndTime(ZonedDateTime.now())
                        .id("some-id")
                        .url("some-url")
                        .system("some-system")
                        .status(ChangeRequestSource.Status.CLOSED)
                        .build())
                .build();
    }
}
