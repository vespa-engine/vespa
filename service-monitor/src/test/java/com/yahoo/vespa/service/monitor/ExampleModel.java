// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExampleModel {

    static final String CLUSTER_ID = "cluster-id";
    static final String SERVICE_NAME = "service-name";
    static final String SERVICE_TYPE = "service-type";
    static final String CONFIG_ID = "config-id";
    static final String TENANT = "tenant";
    static final String APPLICATION_NAME = "application";
    public static final String INSTANCE_NAME = "default";

    static SuperModel createExampleSuperModelWithOneRpcPort(String hostname, int rpcPort) {
        Set<String> tags = Stream.of(SlobrokMonitor2.SLOBROK_RPC_PORT_TAG, "footag")
                .collect(Collectors.toSet());
        Map<String, String> properties = new HashMap<>();
        properties.put(ModelGenerator.CLUSTER_ID_PROPERTY_NAME, CLUSTER_ID);
        Set<PortInfo> portInfos = Stream.of(new PortInfo(rpcPort, tags)).collect(Collectors.toSet());
        ServiceInfo serviceInfo = new ServiceInfo(
                SERVICE_NAME,
                SERVICE_TYPE,
                portInfos,
                properties,
                CONFIG_ID,
                hostname);
        List<ServiceInfo> serviceInfos = Stream.of(serviceInfo).collect(Collectors.toList());
        HostInfo hostInfo = new HostInfo(hostname, serviceInfos);
        List<HostInfo> hostInfos = Stream.of(hostInfo).collect(Collectors.toList());

        TenantName tenantName = TenantName.from(TENANT);
        ApplicationName applicationName = ApplicationName.from(APPLICATION_NAME);
        InstanceName instanceName = InstanceName.from(INSTANCE_NAME);
        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, instanceName);
        Model model = mock(Model.class);
        when(model.getHosts()).thenReturn(hostInfos);
        ApplicationInfo applicationInfo = new ApplicationInfo(applicationId, 1l, model);

        Map<TenantName, Map<ApplicationId, ApplicationInfo>> applicationInfos = new HashMap<>();
        applicationInfos.put(tenantName, new HashMap<>());
        applicationInfos.get(tenantName).put(applicationId, applicationInfo);
        return new SuperModel(applicationInfos);
    }
}
