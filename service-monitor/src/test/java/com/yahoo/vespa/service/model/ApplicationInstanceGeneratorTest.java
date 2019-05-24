// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApplicationInstanceGeneratorTest {
    private static final String configServer1 = "cfg1.yahoo.com";
    private static final String configServer2 = "cfg2.yahoo.com";
    private static final String configServer3 = "cfg3.yahoo.com";
    private static final List<String> configServerList = Stream.of(
            configServer1,
            configServer2,
            configServer3).collect(Collectors.toList());
    private static final ConfigServerApplication configServerApplication = new ConfigServerApplication();

    private final ServiceStatusProvider statusProvider = mock(ServiceStatusProvider.class);

    @Test
    public void toApplicationInstance() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(ServiceStatus.NOT_CHECKED));
        Zone zone = mock(Zone.class);
        ApplicationInfo configServer = configServerApplication.makeApplicationInfo(
                configServerList.stream().map(HostName::from).collect(Collectors.toList()));
        ApplicationInstance applicationInstance = new ApplicationInstanceGenerator(configServer, zone)
                .makeApplicationInstance(statusProvider);

        assertEquals(
                configServerApplication.getApplicationInstanceId(),
                applicationInstance.applicationInstanceId());
        assertEquals(
                configServerApplication.getTenantId(),
                applicationInstance.tenantId());

        assertEquals(
                configServerApplication.getTenantId().toString() +
                        ":" + configServerApplication.getApplicationInstanceId(),
                applicationInstance.reference().toString());

        assertEquals(
                configServerApplication.getClusterId(),
                applicationInstance.serviceClusters().iterator().next().clusterId());

        assertEquals(
                ServiceStatus.NOT_CHECKED,
                applicationInstance
                        .serviceClusters().iterator().next()
                        .serviceInstances().iterator().next()
                        .serviceStatus());

        assertTrue(configServerList.contains(
                applicationInstance
                        .serviceClusters().iterator().next()
                        .serviceInstances().iterator().next()
                        .hostName()
                        .toString()));
    }

    @Test
    public void verifyOnlyNodeAdminServiceIsLeft() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(ServiceStatus.NOT_CHECKED));

        String host1 = "host1";
        String host2 = "host2";

        List<ServiceInfo> serviceInfos1 = List.of(
                makeServiceInfo("metrics", "metricsproxy-container", host1)
        );

        List<ServiceInfo> serviceInfos2 = List.of(
                makeServiceInfo("metrics", "metricsproxy-container", host2),
                makeServiceInfo(ZoneApplication.getNodeAdminClusterId().s(),
                        ZoneApplication.getNodeAdminServiceType().s(), host2)
        );

        List<HostInfo> hostInfos = List.of(
                new HostInfo(host1, serviceInfos1),
                new HostInfo(host2, serviceInfos2)
        );

        Model model = mock(Model.class);
        when(model.getHosts()).thenReturn(hostInfos);

        ApplicationInfo applicationInfo = new ApplicationInfo(ZoneApplication.getApplicationId(), 0, model);

        Zone zone = mock(Zone.class);
        when(zone.environment()).thenReturn(Environment.prod);
        when(zone.region()).thenReturn(RegionName.from("us-east-1"));

        ApplicationInstanceGenerator generator = new ApplicationInstanceGenerator(applicationInfo, zone);
        ApplicationInstance applicationInstance = generator.makeApplicationInstance(statusProvider);

        Map<ClusterId, List<ServiceCluster>> serviceClusters =
                applicationInstance.serviceClusters().stream().collect(Collectors.groupingBy(ServiceCluster::clusterId));
        assertEquals(2, serviceClusters.size());
        List<ServiceCluster> nodeAdminClusters = serviceClusters.get(ZoneApplication.getNodeAdminClusterId());
        assertNotNull(nodeAdminClusters);
        assertEquals(1, nodeAdminClusters.size());
        ServiceCluster nodeAdminCluster = nodeAdminClusters.iterator().next();
        assertEquals(1, nodeAdminCluster.serviceInstances().size());
        assertEquals(host2, nodeAdminCluster.serviceInstances().iterator().next().hostName().s());

        List<ServiceCluster> metricsClusters = serviceClusters.get(new ClusterId("metrics"));
        assertNotNull(metricsClusters);
        assertEquals(1, metricsClusters.size());
        ServiceCluster metricsCluster = metricsClusters.iterator().next();

        // The metrics service on the node admin host is ignored
        assertEquals(1, metricsCluster.serviceInstances().size());
        assertEquals(host1, metricsCluster.serviceInstances().iterator().next().hostName().s());
    }

    private ServiceInfo makeServiceInfo(String clusterId, String serviceType, String hostname) {
        var properties = Map.of(ApplicationInstanceGenerator.CLUSTER_ID_PROPERTY_NAME, clusterId);
        return new ServiceInfo("servicename", serviceType, List.of(), properties, "configid", hostname);
    }
}