// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ConfigserverUtil;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.slobrok.SlobrokMonitorManagerImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModelGeneratorTest {
    private final String ENVIRONMENT = "prod";
    private final String REGION = "us-west-1";
    private final String HOSTNAME = "hostname";
    private final int PORT = 2;
    private final ConfigServerApplication configServerApplication = new ConfigServerApplication();

    @Test
    public void toApplicationModel() throws Exception {
        Zone zone = new Zone(Environment.from(ENVIRONMENT), RegionName.from(REGION));
        ModelGenerator modelGenerator = new ModelGenerator(zone);


        SlobrokMonitorManagerImpl slobrokMonitorManager = mock(SlobrokMonitorManagerImpl.class);
        when(slobrokMonitorManager.getStatus(any(), any(), any(), any()))
                .thenReturn(new ServiceStatusInfo(ServiceStatus.UP));

        ServiceModel serviceModel =
                modelGenerator.toServiceModel(
                        getExampleApplicationInfos(),
                        slobrokMonitorManager);

        Map<ApplicationInstanceReference,
                ApplicationInstance> applicationInstances =
                serviceModel.getAllApplicationInstances();

        assertEquals(2, applicationInstances.size());

        Iterator<Map.Entry<ApplicationInstanceReference,
                        ApplicationInstance>> iterator =
                applicationInstances.entrySet().iterator();

        ApplicationInstance applicationInstance1 = iterator.next().getValue();
        ApplicationInstance applicationInstance2 = iterator.next().getValue();

        if (applicationInstance1.applicationInstanceId().equals(configServerApplication.getApplicationInstanceId())) {
            verifyConfigServerApplication(applicationInstance1);
            verifyOtherApplication(applicationInstance2);
        } else {
            verifyConfigServerApplication(applicationInstance2);
            verifyOtherApplication(applicationInstance1);
        }
    }

    private List<ApplicationInfo> getExampleApplicationInfos() {
        List<ApplicationInfo> applicationInfos = new ArrayList<>();
        ConfigserverConfig config = ConfigserverUtil.createExampleConfigserverConfig();
        applicationInfos.add(ConfigserverUtil.makeExampleConfigServer());
        applicationInfos.addAll(ExampleModel.createExampleSuperModelWithOneRpcPort(HOSTNAME, PORT).getAllApplicationInfos());
        return applicationInfos;
    }

    private void verifyOtherApplication(ApplicationInstance applicationInstance) {
        assertEquals(String.format("%s:%s:%s:%s:%s",
                ExampleModel.TENANT,
                ExampleModel.APPLICATION_NAME,
                ENVIRONMENT,
                REGION,
                ExampleModel.INSTANCE_NAME),
                applicationInstance.reference().toString());

        assertEquals(ExampleModel.TENANT, applicationInstance.tenantId().toString());
        Set<ServiceCluster> serviceClusters =
                applicationInstance.serviceClusters();
        assertEquals(1, serviceClusters.size());
        ServiceCluster serviceCluster = serviceClusters.iterator().next();
        assertEquals(ExampleModel.CLUSTER_ID, serviceCluster.clusterId().toString());
        assertEquals(ExampleModel.SERVICE_TYPE, serviceCluster.serviceType().toString());
        Set<ServiceInstance> serviceInstances =
                serviceCluster.serviceInstances();
        assertEquals(1, serviceClusters.size());
        ServiceInstance serviceInstance = serviceInstances.iterator().next();
        assertEquals(HOSTNAME, serviceInstance.hostName().toString());
        assertEquals(ExampleModel.CONFIG_ID, serviceInstance.configId().toString());
        assertEquals(ServiceStatus.UP, serviceInstance.serviceStatus());
    }

    private void verifyConfigServerApplication(
            ApplicationInstance applicationInstance) {
        assertEquals(configServerApplication.getApplicationInstanceId(), applicationInstance.applicationInstanceId());
    }
}
