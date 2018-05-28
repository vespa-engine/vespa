// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.internal.slobrok.SlobrokMonitorManagerImpl;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SuperModelListenerImplTest {
    @Test
    public void sanityCheck() {
        SlobrokMonitorManagerImpl slobrokMonitorManager = mock(SlobrokMonitorManagerImpl.class);
        ServiceMonitorMetrics metrics = mock(ServiceMonitorMetrics.class);
        ModelGenerator modelGenerator = mock(ModelGenerator.class);
        Zone zone = mock(Zone.class);
        SuperModelListenerImpl listener = new SuperModelListenerImpl(
                slobrokMonitorManager,
                metrics,
                modelGenerator,
                zone);

        SuperModelProvider superModelProvider = mock(SuperModelProvider.class);
        SuperModel superModel = mock(SuperModel.class);
        when(superModelProvider.snapshot(listener)).thenReturn(superModel);

        ApplicationInfo application1 = mock(ApplicationInfo.class);
        ApplicationInfo application2 = mock(ApplicationInfo.class);
        List<ApplicationInfo> applications = Stream.of(application1, application2)
                .collect(Collectors.toList());
        when(superModel.getAllApplicationInfos()).thenReturn(applications);

        listener.start(superModelProvider);
        verify(slobrokMonitorManager).applicationActivated(superModel, application1);
        verify(slobrokMonitorManager).applicationActivated(superModel, application2);

        ServiceModel serviceModel = listener.get();
        verify(modelGenerator).toServiceModel(superModel, zone, slobrokMonitorManager);
    }
}