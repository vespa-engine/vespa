// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.slobrok.SlobrokMonitorManagerImpl;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceModelProviderTest {
    @Test
    public void sanityCheck() {
        SlobrokMonitorManagerImpl slobrokMonitorManager = mock(SlobrokMonitorManagerImpl.class);
        DuperModelManager duperModelManager = mock(DuperModelManager.class);
        ModelGenerator modelGenerator = mock(ModelGenerator.class);
        ServiceModelProvider provider = new ServiceModelProvider(
                slobrokMonitorManager,
                mock(ServiceMonitorMetrics.class),
                duperModelManager,
                modelGenerator);

        ApplicationInfo application1 = mock(ApplicationInfo.class);
        ApplicationInfo application2 = mock(ApplicationInfo.class);
        List<ApplicationInfo> applications = Stream.of(application1, application2)
                .collect(Collectors.toList());
        when(duperModelManager.getApplicationInfos()).thenReturn(applications);

        ServiceModel serviceModel = provider.getServiceModelSnapshot();
        verify(duperModelManager, times(1)).getApplicationInfos();
        verify(modelGenerator).toServiceModel(applications, slobrokMonitorManager);
    }
}