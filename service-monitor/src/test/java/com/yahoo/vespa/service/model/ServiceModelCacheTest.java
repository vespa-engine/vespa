// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceModelCacheTest {
    private final ServiceMonitor expensiveServiceMonitor = mock(ServiceMonitor.class);
    private final Timer timer = mock(Timer.class);
    private final ServiceModelCache cache = new ServiceModelCache(expensiveServiceMonitor, timer);

    @Test
    public void sanityCheck() {
        ServiceModel serviceModel = mock(ServiceModel.class);
        when(expensiveServiceMonitor.getServiceModelSnapshot()).thenReturn(serviceModel);

        long timeMillis = 0;
        when(timer.currentTimeMillis()).thenReturn(timeMillis);

        // Will always populate cache the first time
        ServiceModel actualServiceModel = cache.getServiceModelSnapshot();
        assertTrue(actualServiceModel == serviceModel);
        verify(expensiveServiceMonitor, times(1)).getServiceModelSnapshot();

        // Cache hit
        timeMillis += ServiceModelCache.EXPIRY_MILLIS / 2;
        when(timer.currentTimeMillis()).thenReturn(timeMillis);
        actualServiceModel = cache.getServiceModelSnapshot();
        assertTrue(actualServiceModel == serviceModel);

        // Cache expired
        timeMillis += ServiceModelCache.EXPIRY_MILLIS + 1;
        when(timer.currentTimeMillis()).thenReturn(timeMillis);

        ServiceModel serviceModel2 = mock(ServiceModel.class);
        when(expensiveServiceMonitor.getServiceModelSnapshot()).thenReturn(serviceModel2);

        actualServiceModel = cache.getServiceModelSnapshot();
        assertTrue(actualServiceModel == serviceModel2);
        // '2' because it's cumulative with '1' from the first times(1).
        verify(expensiveServiceMonitor, times(2)).getServiceModelSnapshot();

        // Cache hit #2
        timeMillis += 1;
        when(timer.currentTimeMillis()).thenReturn(timeMillis);
        actualServiceModel = cache.getServiceModelSnapshot();
        assertTrue(actualServiceModel == serviceModel2);
    }
}