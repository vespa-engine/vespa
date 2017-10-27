// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.restapi.wire.SlobrokEntryResponse;
import com.yahoo.vespa.service.monitor.SlobrokMonitorManager;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InstanceResourceTest {
    private static final String APPLICATION_INSTANCE_REFERENCE = "tenant:app:prod:us-west-1:instance";
    private static final ApplicationId APPLICATION_ID = ApplicationId.from(
            "tenant", "app", "instance");
    private static final List<Mirror.Entry> ENTRIES = Arrays.asList(
            new Mirror.Entry("name1", "spec1"),
            new Mirror.Entry("name2", "spec2"));

    private final SlobrokMonitorManager slobrokMonitorManager = mock(SlobrokMonitorManager.class);
    private final InstanceResource resource = new InstanceResource(
            null,
            null,
            slobrokMonitorManager);

    @Test
    public void testGetSlobrokEntries() throws Exception {
        testGetSlobrokEntriesWith("foo", "foo");
    }

    @Test
    public void testGetSlobrokEntriesWithoutPattern() throws Exception {
        testGetSlobrokEntriesWith(null, InstanceResource.DEFAULT_SLOBROK_PATTERN);
    }

    @Test
    public void testGetServiceStatus() {
        ServiceType serviceType = new ServiceType("serviceType");
        ConfigId configId = new ConfigId("configId");
        ServiceStatus serviceStatus = ServiceStatus.UP;
        when(slobrokMonitorManager.getStatus(APPLICATION_ID, serviceType, configId))
                .thenReturn(serviceStatus);
        ServiceStatus actualServiceStatus = resource.getServiceStatus(
                APPLICATION_INSTANCE_REFERENCE,
                serviceType.s(),
                configId.s());
        verify(slobrokMonitorManager).getStatus(APPLICATION_ID, serviceType, configId);
        assertEquals(serviceStatus, actualServiceStatus);
    }

    @Test(expected = WebApplicationException.class)
    public void testBadRequest() {
        resource.getServiceStatus(APPLICATION_INSTANCE_REFERENCE, null, null);
    }

    private void testGetSlobrokEntriesWith(String pattern, String expectedLookupPattern)
            throws Exception{
        when(slobrokMonitorManager.lookup(APPLICATION_ID, expectedLookupPattern))
                .thenReturn(ENTRIES);

        List<SlobrokEntryResponse> response = resource.getSlobrokEntries(
                APPLICATION_INSTANCE_REFERENCE,
                pattern);

        verify(slobrokMonitorManager).lookup(APPLICATION_ID, expectedLookupPattern);

        ObjectMapper mapper = new ObjectMapper();
        String actualJson = mapper.writeValueAsString(response);
        assertEquals(
                "[{\"name\":\"name1\",\"spec\":\"spec1\"},{\"name\":\"name2\",\"spec\":\"spec2\"}]",
                actualJson);
    }
}