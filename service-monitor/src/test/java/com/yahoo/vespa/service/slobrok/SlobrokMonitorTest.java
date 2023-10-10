// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.slobrok;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.vespa.service.model.ExampleModel;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SlobrokMonitorTest {
    private final SlobrokList slobrokList = mock(SlobrokList.class);
    private final Mirror mirror = mock(Mirror.class);
    private final SlobrokMonitor slobrokMonitor = new SlobrokMonitor(slobrokList, mirror);

    @Test
    public void testUpdateSlobrokList() {
        ApplicationInfo applicationInfo = ExampleModel.createApplication(
                "tenant",
                "application-name")
                .build();
    }

    @Test
    public void testUpdateSlobrokList2() {
        final String hostname = "hostname";
        final int port = 1;

        SuperModel superModel = ExampleModel.createExampleSuperModelWithOneRpcPort(hostname, port);
        slobrokMonitor.updateSlobrokList(superModel.getApplicationInfo(ExampleModel.APPLICATION_ID).get());

        String[] expectedSpecs = new String[] {"tcp/" + hostname + ":" + port};
        verify(slobrokList).setup(expectedSpecs);
    }
}
