// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import org.junit.Test;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

public class SuperModelListenerImplTest {
    private final SlobrokMonitor2 slobrokMonitor = mock(SlobrokMonitor2.class);
    private final SuperModel superModel = mock(SuperModel.class);
    private final ApplicationId applicationId = ApplicationId.defaultId();
    private final SuperModelListenerImpl listener = new SuperModelListenerImpl(slobrokMonitor);

    @Test
    public void testActivateApplication() {
        listener.applicationActivated(superModel, applicationId);
        doNothing().when(slobrokMonitor).updateSlobrokList(superModel);
    }

    @Test
    public void testRemoveApplication() {
        listener.applicationRemoved(superModel, applicationId);
        doNothing().when(slobrokMonitor).updateSlobrokList(superModel);
    }
}