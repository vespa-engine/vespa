// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource.Status;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.MockChangeRequestClient;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author olaa
 */
public class ChangeRequestMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final MockChangeRequestClient changeRequestClient = tester.serviceRegistry().changeRequestClient();
    private final ChangeRequestMaintainer changeRequestMaintainer = new ChangeRequestMaintainer(tester.controller(), Duration.ofMinutes(1));

    @Test
    void updates_status_time_and_approval() {
        var time = ZonedDateTime.now();
        var persistedChangeRequest = persistedChangeRequest("some-id", time.minusDays(5), Status.WAITING_FOR_APPROVAL);
        tester.curator().writeChangeRequest(persistedChangeRequest);

        var updatedChangeRequest = newChangeRequest("some-id", ChangeRequest.Approval.APPROVED, time, Status.CANCELED);
        changeRequestClient.setUpcomingChangeRequests(List.of(updatedChangeRequest));
        changeRequestMaintainer.maintain();

        persistedChangeRequest  = tester.curator().readChangeRequest("some-id").get();
        assertEquals(Status.CANCELED, persistedChangeRequest.getChangeRequestSource().getStatus());
        assertEquals(ChangeRequest.Approval.APPROVED, persistedChangeRequest.getApproval());
        assertEquals(time, persistedChangeRequest.getChangeRequestSource().getPlannedStartTime());
        assertEquals(0, changeRequestClient.getApprovedChangeRequests().size());
    }

    @Test
    void deletes_old_change_requests() {
        var now = ZonedDateTime.now();
        var before = now.minus(Duration.ofDays(8));
        var newChangeRequest = persistedChangeRequest("new", now, Status.CLOSED);
        var oldChangeRequest = persistedChangeRequest("old", before, Status.CLOSED);

        tester.curator().writeChangeRequest(newChangeRequest);
        tester.curator().writeChangeRequest(oldChangeRequest);

        changeRequestMaintainer.maintain();

        var persistedChangeRequests = tester.curator().readChangeRequests();
        assertEquals(1, persistedChangeRequests.size());
        assertEquals(newChangeRequest, persistedChangeRequests.get(0));
    }

    @Test
    void approves_change_request_if_non_prod() {
        var time = ZonedDateTime.now();
        var prodChangeRequest = newChangeRequest("id1", ChangeRequest.Approval.REQUESTED, time, Status.WAITING_FOR_APPROVAL);
        var nonProdApprovalRequested = newChangeRequest("id2", "unknown-node", ChangeRequest.Approval.REQUESTED, time, Status.WAITING_FOR_APPROVAL);
        var nonProdApproved = newChangeRequest("id3", "unknown-node", ChangeRequest.Approval.APPROVED, time, Status.WAITING_FOR_APPROVAL);

        changeRequestClient.setUpcomingChangeRequests(List.of(
                prodChangeRequest,
                nonProdApprovalRequested,
                nonProdApproved
        ));
        changeRequestMaintainer.maintain();

        var persistedChangeRequests = tester.curator().readChangeRequests();
        assertEquals(1, persistedChangeRequests.size());
        assertEquals(prodChangeRequest.getId(), persistedChangeRequests.get(0).getId());

        assertEquals(1, changeRequestClient.getApprovedChangeRequests().size());
        assertEquals(nonProdApprovalRequested.getId(), changeRequestClient.getApprovedChangeRequests().get(0).getId());
    }

    private ChangeRequest newChangeRequest(String id, ChangeRequest.Approval approval, ZonedDateTime time, Status status) {
        return newChangeRequest(id, "node-1-tenant-host-prod.us-east-3", approval, time, status);
    }

    private ChangeRequest newChangeRequest(String id, String hostname, ChangeRequest.Approval approval, ZonedDateTime time, Status status) {
        return new ChangeRequest.Builder()
                .id(id)
                .approval(approval)
                .impact(ChangeRequest.Impact.VERY_HIGH)
                .impactedSwitches(List.of())
                .impactedHosts(List.of(hostname))
                .changeRequestSource(new ChangeRequestSource.Builder()
                        .plannedStartTime(time)
                        .plannedEndTime(time)
                        .id("some-id")
                        .url("some-url")
                        .system("some-system")
                        .status(status)
                        .build())
                .build();
    }

    private VespaChangeRequest persistedChangeRequest(String id, ZonedDateTime time, Status status) {
        return new VespaChangeRequest(
                newChangeRequest(id, ChangeRequest.Approval.REQUESTED, time, status),
                ZoneId.from("prod.us-east-3")
        );
    }
}
