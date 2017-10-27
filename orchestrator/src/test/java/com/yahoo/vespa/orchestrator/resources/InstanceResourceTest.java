// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.vespa.service.monitor.SlobrokMonitorManager;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InstanceResourceTest {
    @Test
    public void testGetSlobrokEntries() throws Exception {
        SlobrokMonitorManager slobrokMonitorManager = mock(SlobrokMonitorManager.class);
        InstanceResource resource = new InstanceResource(
                null,
                null,
                slobrokMonitorManager);

        ApplicationId applicationId = ApplicationId.from("tenant", "app", "instance");
        String pattern = "foo";

        List<Mirror.Entry> entries = Arrays.asList(
                new Mirror.Entry("name1", "spec1"),
                new Mirror.Entry("name2", "spec2"));

        when(slobrokMonitorManager.lookup(applicationId, pattern)).thenReturn(entries);

        List<SlobrokEntryResponse> response = resource.getSlobrokEntries(
                "tenant:app:prod:us-west-1:instance",
                pattern);

        verify(slobrokMonitorManager).lookup(applicationId, pattern);

        ObjectMapper mapper = new ObjectMapper();
        String actualJson = mapper.writeValueAsString(response);
        assertEquals(
                "[{\"name\":\"name1\",\"spec\":\"spec1\"},{\"name\":\"name2\",\"spec\":\"spec2\"}]",
                actualJson);
    }
}