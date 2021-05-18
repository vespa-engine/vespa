// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource.Status;
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
    public void updates_status_time_and_approval() {
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
    }

    @Test
    public void deletes_old_change_requests() {
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

    private ChangeRequest newChangeRequest(String id, ChangeRequest.Approval approval) {
        return newChangeRequest(id, approval, ZonedDateTime.now(), Status.CLOSED);
    }

    private ChangeRequest newChangeRequest(String id, ChangeRequest.Approval approval, ZonedDateTime time, Status status) {
        return new ChangeRequest.Builder()
                .id(id)
                .approval(approval)
                .impact(ChangeRequest.Impact.VERY_HIGH)
                .impactedSwitches(List.of())
                .impactedHosts(List.of("node-1-tenant-host-prod.us-east-3"))
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
