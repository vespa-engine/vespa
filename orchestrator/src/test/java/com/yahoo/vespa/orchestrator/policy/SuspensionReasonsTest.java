// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SuspensionReasonsTest {
    private final ManualClock clock = new ManualClock(Instant.ofEpochMilli(1600440708123L));
    private final ServiceInstance service = mock(ServiceInstance.class);
    private final ServiceInstance service2 = mock(ServiceInstance.class);

    @Test
    public void test() {
        assertEquals(Optional.empty(), new SuspensionReasons().makeLogMessage());
        assertEquals(Optional.empty(), SuspensionReasons.nothingNoteworthy().makeLogMessage());

        when(service.hostName()).thenReturn(new HostName("host1"));
        when(service.descriptiveName()).thenReturn("service1");
        when(service2.hostName()).thenReturn(new HostName("host2"));
        when(service2.descriptiveName()).thenReturn("service2");

        var reasons = SuspensionReasons.downSince(service, clock.instant(), Duration.ofSeconds(30));
        reasons.mergeWith(SuspensionReasons.isDown(service2));
        assertEquals(Optional.of(
                "host1 suspended because service1 has been down since 2020-09-18T14:51:48Z (30 seconds); " +
                "host2 suspended because service2 is down"),
                reasons.makeLogMessage());

    }

}
