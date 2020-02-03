// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class ZookeeperStatusService2Test {
    private final Curator curator = mock(Curator.class);
    private final Timer timer = new TestTimer();
    private final Metric metric = mock(Metric.class);
    private final HostInfosCache cache = mock(HostInfosCache.class);
    private final ZookeeperStatusService zookeeperStatusService = new ZookeeperStatusService(curator, metric, timer, cache);

    private final OrchestratorContext context = mock(OrchestratorContext.class);
    private final InterProcessMutex mutex = mock(InterProcessMutex.class);
    private final ApplicationInstanceReference reference = new ApplicationInstanceReference(
            new TenantId("tenant"), new ApplicationInstanceId("app:dev:us-east-1:default"));

    @Test
    public void verifyLocks() throws Exception {
        when(context.isProbe()).thenReturn(true);
        when(context.largeLocks()).thenReturn(false);
        when(context.partOfMultiAppOp()).thenReturn(true);

        when(curator.createMutex(any())).thenReturn(mutex);
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        when(context.getTimeLeft()).thenReturn(Duration.ofSeconds(12));

        try (MutableStatusRegistry registry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(context, reference)) {
            // nothing
        }

        verify(curator, times(1)).createMutex(any());
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(1)).release();
        verify(context, times(0)).runOnClose(any());
        verifyNoMoreInteractions(mutex);

        // Now the non-probe suspension

        when(context.isProbe()).thenReturn(false);

        try (MutableStatusRegistry registry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(context, reference)) {
            // nothing
        }

        verify(mutex, times(2)).acquire(anyLong(), any());
        verify(mutex, times(2)).release();
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(context, times(0)).runOnClose(runnableCaptor.capture());
        verifyNoMoreInteractions(mutex);
    }

    @Test
    public void verifyLargeLocks() throws Exception {
        when(context.isProbe()).thenReturn(true);
        when(context.largeLocks()).thenReturn(true);
        when(context.partOfMultiAppOp()).thenReturn(true);

        when(curator.createMutex(any())).thenReturn(mutex);
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        when(context.getTimeLeft()).thenReturn(Duration.ofSeconds(12));

        try (MutableStatusRegistry registry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(context, reference)) {
            // nothing
        }

        verify(curator, times(1)).createMutex(any());
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(0)).release();
        verify(context, times(1)).runOnClose(any());
        verifyNoMoreInteractions(mutex);

        // Now the non-probe suspension

        when(context.isProbe()).thenReturn(false);

        try (MutableStatusRegistry registry = zookeeperStatusService.lockApplicationInstance_forCurrentThreadOnly(context, reference)) {
            // nothing
        }

        // No (additional) acquire, and no releases.
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(0)).release();
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(context, times(1)).runOnClose(runnableCaptor.capture());
        verifyNoMoreInteractions(mutex);

        // Verify the context runnable releases the mutex
        assertEquals(1, runnableCaptor.getAllValues().size());
        runnableCaptor.getAllValues().forEach(Runnable::run);
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(1)).release();

    }
}