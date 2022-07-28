// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author olaa
 */
public class ChangeRequestSerializerTest {

    @Test
    void reserialization_equality() {
        var source = new ChangeRequestSource("aws", "id321", "url", ChangeRequestSource.Status.STARTED, ZonedDateTime.now(), ZonedDateTime.now());
        var actionPlan = List.of(
                new HostAction("host1", HostAction.State.RETIRING, Instant.now()),
                new HostAction("host2", HostAction.State.RETIRED, Instant.now())
        );

        var changeRequest = new VespaChangeRequest(
                "id123",
                source,
                List.of("switch1"),
                List.of("host1", "host2"),
                ChangeRequest.Approval.APPROVED,
                ChangeRequest.Impact.VERY_HIGH,
                VespaChangeRequest.Status.IN_PROGRESS,
                actionPlan,
                ZoneId.defaultId()
        );

        var reserialized = ChangeRequestSerializer.fromSlime(ChangeRequestSerializer.toSlime(changeRequest));
        assertEquals(changeRequest, reserialized);
    }

}
