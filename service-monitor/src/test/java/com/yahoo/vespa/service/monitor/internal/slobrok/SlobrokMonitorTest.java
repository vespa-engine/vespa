// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.slobrok;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.vespa.service.monitor.internal.ExampleModel;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class SlobrokMonitorTest {
    private final SlobrokList slobrokList = mock(SlobrokList.class);
    private final Mirror mirror = mock(Mirror.class);
    private SlobrokMonitor slobrokMonitor = new SlobrokMonitor(slobrokList, mirror);

    @Test
    public void testUpdateSlobrokList() {
        ApplicationInfo applicationInfo = ExampleModel.createApplication(
                "tenant",
                "application-name")
                .build();
    }

    @Test
    public void testUpdateSlobrokList2() {
        /*
        final String hostname = "hostname";
        final int port = 1;

        SuperModel superModel = ExampleModel.createExampleSuperModelWithOneRpcPort(hostname, port);
        slobrokMonitor.updateSlobrokList(superModel.getApplicationInfo());

        String[] expectedSpecs = new String[] {"tcp/" + hostname + ":" + port};
        verify(slobrokList).setup(expectedSpecs); */
    }
}
