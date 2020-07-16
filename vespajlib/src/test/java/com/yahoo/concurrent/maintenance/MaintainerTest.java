// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author freva
 */
public class MaintainerTest {

    @Test
    public void staggering() {
        List<String> cluster = List.of("cfg1", "cfg2", "cfg3");
        Duration interval = Duration.ofMillis(300);
        Instant now = Instant.ofEpochMilli(1000);
        assertEquals(200, Maintainer.staggeredDelay(interval, now, "cfg1", cluster).toMillis());
        assertEquals(0, Maintainer.staggeredDelay(interval, now, "cfg2", cluster).toMillis());
        assertEquals(100, Maintainer.staggeredDelay(interval, now, "cfg3", cluster).toMillis());

        now = Instant.ofEpochMilli(1001);
        assertEquals(199, Maintainer.staggeredDelay(interval, now, "cfg1", cluster).toMillis());
        assertEquals(299, Maintainer.staggeredDelay(interval, now, "cfg2", cluster).toMillis());
        assertEquals(99, Maintainer.staggeredDelay(interval, now, "cfg3", cluster).toMillis());

        now = Instant.ofEpochMilli(1101);
        assertEquals(99, Maintainer.staggeredDelay(interval, now, "cfg1", cluster).toMillis());
        assertEquals(199, Maintainer.staggeredDelay(interval, now, "cfg2", cluster).toMillis());
        assertEquals(299, Maintainer.staggeredDelay(interval, now, "cfg3", cluster).toMillis());

        assertEquals(300, Maintainer.staggeredDelay(interval, now, "cfg0", cluster).toMillis());
    }

    @Test
    public void success_metric() {
        ManualClock clock = new ManualClock();
        AtomicReference<Instant> lastSuccess = new AtomicReference<>();
        JobMetrics jobMetrics = new JobMetrics(clock, (job, instant) -> lastSuccess.set(instant));
        TestMaintainer maintainer = new TestMaintainer(jobMetrics);

        // Maintainer not successful yet
        maintainer.successOnNextRun(false).run();
        assertNull(lastSuccess.get());

        // Maintainer runs successfully
        clock.advance(Duration.ofHours(1));
        Instant lastSuccess0 = clock.instant();
        maintainer.successOnNextRun(true).run();
        assertEquals(lastSuccess0, lastSuccess.get());

        // Maintainer runs successfully again
        clock.advance(Duration.ofHours(2));
        Instant lastSuccess1 = clock.instant();
        maintainer.run();
        assertEquals(lastSuccess1, lastSuccess.get());

        // Maintainer throws
        clock.advance(Duration.ofHours(5));
        maintainer.throwOnNextRun(true).run();
        assertEquals("Time of successful run is unchanged", lastSuccess1, lastSuccess.get());

        // Maintainer recovers
        clock.advance(Duration.ofHours(3));
        Instant lastSuccess2 = clock.instant();
        maintainer.throwOnNextRun(false).run();
        assertEquals(lastSuccess2, lastSuccess.get());
    }

}
