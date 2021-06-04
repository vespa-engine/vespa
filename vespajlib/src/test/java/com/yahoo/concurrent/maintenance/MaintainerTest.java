// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class MaintainerTest {

    private final JobControl jobControl = new JobControl(new JobControlStateMock());

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
        AtomicLong consecutiveFailures = new AtomicLong();
        JobMetrics jobMetrics = new JobMetrics((job, count) -> consecutiveFailures.set(count));
        TestMaintainer maintainer = new TestMaintainer(null, jobControl, jobMetrics);

        // Maintainer fails twice in a row
        maintainer.successOnNextRun(false).run();
        assertEquals(1, consecutiveFailures.get());
        maintainer.successOnNextRun(false).run();
        assertEquals(2, consecutiveFailures.get());

        // Maintainer runs successfully
        maintainer.successOnNextRun(true).run();
        assertEquals(0, consecutiveFailures.get());

        // Maintainer runs successfully again
        maintainer.run();
        assertEquals(0, consecutiveFailures.get());

        // Maintainer throws
        maintainer.throwOnNextRun(new RuntimeException()).run();
        assertEquals(1, consecutiveFailures.get());

        // Maintainer recovers
        maintainer.throwOnNextRun(null).run();
        assertEquals(0, consecutiveFailures.get());

        // Lock exception is treated as a failure
        maintainer.throwOnNextRun(new UncheckedTimeoutException()).run();
        assertEquals(1, consecutiveFailures.get());
    }

}
