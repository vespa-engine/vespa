// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.service.monitor.internal.slobrok.SlobrokMonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExampleModel {

    static final String CLUSTER_ID = "cluster-id";
    static final String SERVICE_NAME = "service-name";
    static final String SERVICE_TYPE = SlobrokMonitor.SLOBROK_SERVICE_TYPE;
    static final String CONFIG_ID = "configid/1";
    static final String TENANT = "tenant";
    static final String APPLICATION_NAME = "application";
    public static final String INSTANCE_NAME = "default";

    static SuperModel createExampleSuperModelWithOneRpcPort(String hostname, int rpcPort) {
        List<String> hosts = Stream.of(hostname).collect(Collectors.toList());

        ApplicationInfo applicationInfo = ExampleModel
                .createApplication(TENANT, APPLICATION_NAME)
                .addServiceCluster(CLUSTER_ID, SERVICE_NAME, SERVICE_TYPE, hosts)
                .addPort(rpcPort, "footag", SlobrokMonitor.SLOBROK_RPC_PORT_TAG)
                .addPort(rpcPort + 1, "bartag")
                .then()
                .build();

        Map<TenantName, Map<ApplicationId, ApplicationInfo>> applicationInfos = new HashMap<>();
        applicationInfos.put(applicationInfo.getApplicationId().tenant(), new HashMap<>());
        applicationInfos.get(applicationInfo.getApplicationId().tenant())
                .put(applicationInfo.getApplicationId(), applicationInfo);
        return new SuperModel(applicationInfos);
    }

    public static ApplicationBuilder createApplication(String tenant,
                                                       String applicationName) {
        return new ApplicationBuilder(tenant, applicationName);
    }


    public static class ApplicationBuilder {
        private final String tenant;
        private final String applicationName;
        private final List<ClusterBuilder> clusters = new ArrayList<>();

        ApplicationBuilder(String tenant, String applicationName) {
            this.tenant = tenant;
            this.applicationName = applicationName;
        }

        ClusterBuilder addServiceCluster(
                String clusterId,
                String serviceName,
                String serviceType,
                List<String> hosts) {
            return new ClusterBuilder(
                    this,
                    clusterId,
                    serviceName,
                    serviceType,
                    hosts);
        }

        public ApplicationInfo build() {
            List<String> allHosts = clusters.stream()
                    .flatMap(clusterBuilder -> clusterBuilder.hosts.stream())
                    .distinct()
                    .collect(Collectors.toList());

            List<HostInfo> hostInfos = new ArrayList<>();
            for (String hostname : allHosts) {
                List<ServiceInfo> serviceInfos = new ArrayList<>();
                for (ClusterBuilder cluster : clusters) {
                    buildServiceInfo(hostname, cluster).ifPresent(serviceInfos::add);
                }

                HostInfo hostInfo = new HostInfo(hostname, serviceInfos);
                hostInfos.add(hostInfo);
            }

            ApplicationId id = ApplicationId.from(
                    tenant,
                    applicationName,
                    InstanceName.defaultName().toString());

            Model model = mock(Model.class);
            when(model.getHosts()).thenReturn(hostInfos);

            return new ApplicationInfo(id, 1, model);
        }

        private Optional<ServiceInfo> buildServiceInfo(
                String hostname,
                ClusterBuilder cluster) {
            int hostIndex = cluster.hosts.indexOf(hostname);
            if (hostIndex < 0) {
                return Optional.empty();
            }

            Map<String, String> properties = new HashMap<>();
            properties.put(ModelGenerator.CLUSTER_ID_PROPERTY_NAME, cluster.clusterId);
            return Optional.of(new ServiceInfo(
                    cluster.serviceName,
                    cluster.serviceType,
                    cluster.portInfos,
                    properties,
                    "configid/" + (hostIndex + 1),
                    hostname));
        }
    }

    static class ClusterBuilder {
        private final ApplicationBuilder applicationBuilder;
        private final String clusterId;
        private final String serviceName;
        private final String serviceType;
        private final List<String> hosts;
        private final List<PortInfo> portInfos = new ArrayList<>();

        ClusterBuilder(ApplicationBuilder applicationBuilder,
                       String clusterId,
                       String serviceName,
                       String serviceType,
                       List<String> hosts) {
            this.applicationBuilder = applicationBuilder;
            this.clusterId = clusterId;
            this.serviceName = serviceName;
            this.serviceType = serviceType;
            this.hosts = hosts;
        }

        /**
         * A bit unrealistic, but the port is the same on all hosts.
         */
        ClusterBuilder addPort(int port, String... tags) {
            portInfos.add(new PortInfo(port, Arrays.asList(tags)));
            return this;
        }

        ApplicationBuilder then() {
            applicationBuilder.clusters.add(this);
            return applicationBuilder;
        }
    }
}
