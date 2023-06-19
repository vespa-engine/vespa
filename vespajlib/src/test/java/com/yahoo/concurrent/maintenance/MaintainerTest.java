// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import com.yahoo.concurrent.UncheckedTimeoutException;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class MaintainerTest {

    private static final double delta = 0.000001;

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
        TestJobMetrics jobMetrics = new TestJobMetrics();
        TestMaintainer maintainer = new TestMaintainer(null, jobControl, jobMetrics);

        maintainer.successOnNextRun(1.0).run();
        assertEquals(1, jobMetrics.successFactor, delta);
        maintainer.successOnNextRun(0.0).run();
        assertEquals(0, jobMetrics.successFactor, delta);
        maintainer.successOnNextRun(0.1).run();
        assertEquals(0.1, jobMetrics.successFactor, delta);

        // Maintainer throws
        maintainer.throwOnNextRun(new RuntimeException()).run();
        assertEquals(-1, jobMetrics.successFactor, delta);

        // Maintainer recovers
        maintainer.throwOnNextRun(null).run();
        maintainer.successOnNextRun(1.0).run();
        assertEquals(1, jobMetrics.successFactor, delta);

        // Lock exception is treated as a failure
        maintainer.throwOnNextRun(new UncheckedTimeoutException()).run();
        assertEquals(-1, jobMetrics.successFactor, delta);
    }

    private static class TestJobMetrics extends JobMetrics {

        double successFactor = 0.0;
        long durationMs = 0;

        @Override
        public void completed(String job, double successFactor, long durationMs) {
            this.successFactor = successFactor;
            this.durationMs = durationMs;
        }

    }

}
