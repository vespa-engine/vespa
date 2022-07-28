// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.statistic.RateStatistic;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * @author bjorncs
 */
public class ConnectionThrottlerTest {

    @Test
    void throttles_when_any_resource_check_exceeds_configured_threshold() {
        Runtime runtime = mock(Runtime.class);
        when(runtime.maxMemory()).thenReturn(100l);
        RateStatistic rateStatistic = new RateStatistic(1, TimeUnit.HOURS);
        MockScheduler scheduler = new MockScheduler();
        ConnectorConfig.Throttling config = new ConnectorConfig.Throttling(new ConnectorConfig.Throttling.Builder()
                .maxHeapUtilization(0.8)
                .maxAcceptRate(1));

        AbstractConnector connector = mock(AbstractConnector.class);

        ConnectionThrottler throttler = new ConnectionThrottler(runtime, rateStatistic, scheduler, connector, config);

        // Heap utilization above configured threshold, but connection rate below threshold.
        when(runtime.freeMemory()).thenReturn(10l);
        when(connector.isAccepting()).thenReturn(true);
        throttler.onAccepting(null);
        assertNotNull(scheduler.task);
        verify(connector).setAccepting(false);

        // Heap utilization below threshold, but connection rate above threshold.
        when(runtime.freeMemory()).thenReturn(80l);
        rateStatistic.record();
        rateStatistic.record(); // above accept rate limit (2 > 1)
        scheduler.task.run(); // run unthrottleIfBelowThresholds()
        verify(connector, times(1)).setAccepting(anyBoolean()); // verify setAccepting has not been called any mores times

        // Both heap utilization and accept rate below threshold
        when(runtime.freeMemory()).thenReturn(80l);
        when(connector.isAccepting()).thenReturn(false);
        rateStatistic.reset();
        scheduler.task.run(); // run unthrottleIfBelowThresholds()
        verify(connector).setAccepting(true);

        // Both heap utilization and accept rate below threshold
        when(connector.isAccepting()).thenReturn(true);
        when(runtime.freeMemory()).thenReturn(80l);
        rateStatistic.record();
        throttler.onAccepting(null);
        verify(connector, times(2)).setAccepting(anyBoolean()); // verify setAccepting has not been called any mores times
    }

    private static class MockScheduler extends AbstractLifeCycle implements Scheduler {
        Runnable task;

        @Override
        public Task schedule(Runnable task, long delay, TimeUnit units) {
            this.task = task;
            return () -> false;
        }
    }

}