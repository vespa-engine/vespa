// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class HostInfosCacheTest {
    @Test
    public void test() {
        MockCurator curator = new MockCurator();
        HostInfosService service = mock(HostInfosService.class);
        HostInfosCache cache = new HostInfosCache(curator, service);

        ApplicationInstanceReference application = new ApplicationInstanceReference(
                new TenantId("tenantid"),
                new ApplicationInstanceId("application:dev:region:default"));

        HostInfos hostInfos = mock(HostInfos.class);
        when(service.getHostInfos(application)).thenReturn(hostInfos);

        // cache miss
        HostInfos hostInfos1 = cache.getHostInfos(application);
        verify(service, times(1)).getHostInfos(any());
        assertSame(hostInfos1, hostInfos);

        // cache hit
        HostInfos hostInfos2 = cache.getHostInfos(application);
        verify(service, times(1)).getHostInfos(any());
        assertSame(hostInfos2, hostInfos);

        when(service.setHostStatus(any(), any(), any())).thenReturn(true);
        boolean modified = cache.setHostStatus(application, new HostName("hostname1"), HostStatus.ALLOWED_TO_BE_DOWN);
        verify(service, times(1)).getHostInfos(any());
        assertTrue(modified);

        // cache miss
        HostInfos hostInfos3 = cache.getHostInfos(application);
        verify(service, times(2)).getHostInfos(any());
        assertSame(hostInfos1, hostInfos);
    }
}