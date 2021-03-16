package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.MockChangeRequestClient;
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

        var upcomingChangeRequests = List.of(
                newChangeRequest("id1", ChangeRequest.Approval.APPROVED),
                newChangeRequest("id2", ChangeRequest.Approval.REQUESTED)
        );

        changeRequestClient.setUpcomingChangeRequests(upcomingChangeRequests);
        changeRequestMaintainer.maintain();

        var approvedChangeRequests = changeRequestClient.getApprovedChangeRequests();

        assertEquals(1, approvedChangeRequests.size());
        assertEquals("id2", approvedChangeRequests.get(0).getId());
    }

    private ChangeRequest newChangeRequest(String id, ChangeRequest.Approval approval) {
        return new ChangeRequest.Builder()
                .id(id)
                .approval(approval)
                .impact(ChangeRequest.Impact.VERY_HIGH)
                .impactedSwitches(List.of())
                .impactedHosts(List.of())
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