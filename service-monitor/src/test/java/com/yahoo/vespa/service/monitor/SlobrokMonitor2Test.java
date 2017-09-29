// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.SuperModel;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlobrokMonitor2Test {
    private final SlobrokList slobrokList = mock(SlobrokList.class);
    private final Mirror mirror = mock(Mirror.class);
    private SlobrokMonitor2 slobrokMonitor = new SlobrokMonitor2(slobrokList, mirror);

    @Test
    public void testLookup() {
        assertEquals(
                Optional.of("config.id"),
                lookup("topleveldispatch", "config.id"));

        assertEquals(
                Optional.empty(),
                lookup("adminserver", "config.id"));
    }

    private Optional<String> lookup(String serviceType, String configId) {
        return slobrokMonitor.lookup(new ServiceType(serviceType), new ConfigId(configId));
    }

    @Test
    public void testGetStatus() {
        ServiceType serviceType = new ServiceType("topleveldispatch");
        ConfigId configId = new ConfigId("config.id");
        when(mirror.lookup("config.id")).thenReturn(new Mirror.Entry[1]);
        assertEquals(ServiceMonitorStatus.UP, slobrokMonitor.getStatus(serviceType, configId));
    }

    @Test
    public void testUpdateSlobrokList() {
        final String hostname = "hostname";
        final int port = 1;

        SuperModel superModel = ExampleModel.createExampleSuperModelWithOneRpcPort(hostname, port);
        slobrokMonitor.updateSlobrokList(superModel);

        String[] expectedSpecs = new String[] {"tcp/" + hostname + ":" + port};
        verify(slobrokList).setup(expectedSpecs);
    }

}