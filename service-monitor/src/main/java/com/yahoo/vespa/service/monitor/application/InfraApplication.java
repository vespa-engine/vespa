// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.component.Version;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.monitor.internal.ModelGenerator;
import com.yahoo.vespa.service.monitor.internal.health.ApplicationHealthMonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author freva
 */
public abstract class InfraApplication implements InfraApplicationApi {
    static final int HEALTH_PORT = 8080;

    private static final TenantName TENANT_NAME = TenantName.from("hosted-vespa");
    private static final String CONFIG_ID_PREFIX = "configid.";

    private final ApplicationId applicationId;
    private final Capacity capacity;
    private final ClusterSpec.Type clusterType;
    private final ClusterSpec.Id clusterId;
    private final ServiceType serviceType;

    public static ApplicationId createHostedVespaApplicationId(String applicationName) {
        return new ApplicationId.Builder()
                .tenant(TENANT_NAME)
                .applicationName(applicationName)
                .build();
    }

    protected InfraApplication(String applicationName,
                               NodeType nodeType,
                               ClusterSpec.Type clusterType,
                               ClusterSpec.Id clusterId,
                               ServiceType serviceType) {
        this.applicationId = createHostedVespaApplicationId(applicationName);
        this.capacity = Capacity.fromRequiredNodeType(nodeType);
        this.clusterType = clusterType;
        this.clusterId = clusterId;
        this.serviceType = serviceType;
    }

    @Override
    public ApplicationId getApplicationId() {
        return applicationId;
    }

    @Override
    public Capacity getCapacity() {
        return capacity;
    }

    @Override
    public ClusterSpec getClusterSpecWithVersion(Version version) {
        return ClusterSpec.request(clusterType, clusterId, version, true);
    }

    public ClusterSpec.Type getClusterType() {
        return clusterType;
    }

    public ClusterSpec.Id getClusterId() {
        return clusterId;
    }

    public ApplicationInfo makeApplicationInfo(List<HostName> hostnames) {
        List<HostInfo> hostInfos = new ArrayList<>();
        for (int index = 0; index < hostnames.size(); ++index) {
            hostInfos.add(makeHostInfo(hostnames.get(index), HEALTH_PORT, index, serviceType, clusterId));
        }

        return new ApplicationInfo(applicationId, 0, new HostsModel(hostInfos));
    }

    private static HostInfo makeHostInfo(HostName hostname, int port, int configIndex, ServiceType serviceType, ClusterSpec.Id clusterId) {
        PortInfo portInfo = new PortInfo(port, ApplicationHealthMonitor.PORT_TAGS_HEALTH);

        Map<String, String> properties = new HashMap<>();
        properties.put(ModelGenerator.CLUSTER_ID_PROPERTY_NAME, clusterId.value());

        ServiceInfo serviceInfo = new ServiceInfo(
                // service name == service type for the first service of each type on each host
                serviceType.s(),
                serviceType.s(),
                Collections.singletonList(portInfo),
                properties,
                configIdFrom(configIndex).s(),
                hostname.value());

        return new HostInfo(hostname.value(), Collections.singletonList(serviceInfo));
    }

    private static ConfigId configIdFrom(int index) {
        return new ConfigId(CONFIG_ID_PREFIX + index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfraApplication that = (InfraApplication) o;
        return Objects.equals(applicationId, that.applicationId) &&
                Objects.equals(capacity, that.capacity) &&
                clusterType == that.clusterType &&
                Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(serviceType, that.serviceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, capacity, clusterType, clusterId, serviceType);
    }

    @Override
    public String toString() {
        return "InfraApplication{" +
                "applicationId=" + applicationId +
                ", capacity=" + capacity +
                ", clusterType=" + clusterType +
                ", clusterId=" + clusterId +
                ", serviceType=" + serviceType +
                '}';
    }
}
