// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.service.monitor.AntiServiceMonitor;
import com.yahoo.vespa.service.monitor.CriticalRegion;
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
public class ZkStatusService2Test {
    private final Curator curator = mock(Curator.class);
    private final Timer timer = new TestTimer();
    private final Metric metric = mock(Metric.class);
    private final HostInfosCache cache = mock(HostInfosCache.class);
    private final CriticalRegion criticalRegion = mock(CriticalRegion.class);
    private final AntiServiceMonitor antiServiceMonitor = mock(AntiServiceMonitor.class);
    private final ZkStatusService zkStatusService =
            new ZkStatusService(curator, metric, timer, cache, antiServiceMonitor);

    private final OrchestratorContext context = mock(OrchestratorContext.class);
    private final InterProcessMutex mutex = mock(InterProcessMutex.class);
    private final ApplicationInstanceReference reference = new ApplicationInstanceReference(
            new TenantId("tenant"), new ApplicationInstanceId("app:dev:us-east-1:default"));

    @Test
    public void verifyLocks() throws Exception {
        when(context.isProbe()).thenReturn(true);
        when(context.hasLock(any())).thenReturn(false);
        when(context.registerLockAcquisition(any(), any())).thenReturn(false);

        when(curator.createMutex(any())).thenReturn(mutex);
        when(mutex.acquire(anyLong(), any())).thenReturn(true);
        when(antiServiceMonitor.disallowDuperModelLockAcquisition(any())).thenReturn(criticalRegion);

        when(context.getTimeLeft()).thenReturn(Duration.ofSeconds(12));

        verify(antiServiceMonitor, times(0)).disallowDuperModelLockAcquisition(any());
        try (ApplicationLock lock = zkStatusService.lockApplication(context, reference)) {
            verify(antiServiceMonitor, times(1)).disallowDuperModelLockAcquisition(any());

            verify(criticalRegion, times(0)).close();
        }
        verify(criticalRegion, times(1)).close();

        verify(curator, times(1)).createMutex(any());
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(1)).release();
        verify(context, times(1)).hasLock(any());
        verify(context, times(1)).registerLockAcquisition(any(), any());
        verifyNoMoreInteractions(mutex, antiServiceMonitor, criticalRegion);

        // Now the non-probe suspension

        when(context.isProbe()).thenReturn(false);

        verify(antiServiceMonitor, times(1)).disallowDuperModelLockAcquisition(any());
        try (ApplicationLock lock = zkStatusService.lockApplication(context, reference)) {
            verify(antiServiceMonitor, times(2)).disallowDuperModelLockAcquisition(any());

            verify(criticalRegion, times(1)).close();
        }
        verify(criticalRegion, times(2)).close();

        verify(mutex, times(2)).acquire(anyLong(), any());
        verify(mutex, times(2)).release();
        verify(context, times(2)).hasLock(any());
        verify(context, times(2)).registerLockAcquisition(any(), any());
        verifyNoMoreInteractions(mutex, antiServiceMonitor, criticalRegion);
    }

    @Test
    public void verifyLargeLocks() throws Exception {
        when(context.isProbe()).thenReturn(true);
        when(context.hasLock(any())).thenReturn(false);
        when(context.registerLockAcquisition(any(), any())).thenReturn(true);

        when(curator.createMutex(any())).thenReturn(mutex);
        when(mutex.acquire(anyLong(), any())).thenReturn(true);
        when(antiServiceMonitor.disallowDuperModelLockAcquisition(any())).thenReturn(criticalRegion);

        when(context.getTimeLeft()).thenReturn(Duration.ofSeconds(12));

        try (ApplicationLock lock = zkStatusService.lockApplication(context, reference)) {
            // nothing
        }

        verify(curator, times(1)).createMutex(any());
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(0)).release();
        verify(context, times(1)).hasLock(any());
        verify(context, times(1)).registerLockAcquisition(any(), any());
        verify(antiServiceMonitor, times(1)).disallowDuperModelLockAcquisition(any());
        verify(criticalRegion, times(0)).close();
        verifyNoMoreInteractions(mutex, antiServiceMonitor, criticalRegion);

        // Now the non-probe suspension

        when(context.isProbe()).thenReturn(false);
        when(context.hasLock(any())).thenReturn(true);
        when(context.registerLockAcquisition(any(), any())).thenReturn(false);

        try (ApplicationLock lock = zkStatusService.lockApplication(context, reference)) {
            // nothing
        }

        // No (additional) acquire, and no releases.
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(0)).release();
        verify(context, times(2)).hasLock(any());
        verify(context, times(1)).registerLockAcquisition(any(), any());
        verify(antiServiceMonitor, times(1)).disallowDuperModelLockAcquisition(any());
        verify(criticalRegion, times(0)).close();
        verifyNoMoreInteractions(mutex, antiServiceMonitor, criticalRegion);

        // Verify the context runnable releases the mutex
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(context, times(1)).registerLockAcquisition(any(), runnableCaptor.capture());
        assertEquals(1, runnableCaptor.getAllValues().size());
        runnableCaptor.getAllValues().forEach(Runnable::run);
        verify(mutex, times(1)).acquire(anyLong(), any());
        verify(mutex, times(1)).release();
        verify(criticalRegion, times(1)).close();
        verifyNoMoreInteractions(mutex, antiServiceMonitor, criticalRegion);
    }
}