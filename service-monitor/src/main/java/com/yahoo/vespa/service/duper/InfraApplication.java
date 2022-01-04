// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.service.health.StateV1HealthModel;
import com.yahoo.vespa.service.model.ApplicationInstanceGenerator;
import com.yahoo.vespa.service.model.ModelGenerator;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public abstract class InfraApplication implements InfraApplicationApi {

    private final InfrastructureApplication application;
    private final Capacity capacity;
    private final ClusterSpec.Type clusterSpecType;
    private final ClusterSpec.Id clusterSpecId;
    private final ServiceType serviceType;
    private final int healthPort;

    protected InfraApplication(InfrastructureApplication application,
                               ClusterSpec.Type clusterSpecType,
                               ClusterSpec.Id clusterSpecId,
                               ServiceType serviceType,
                               int healthPort) {
        this.application = application;
        this.capacity = Capacity.fromRequiredNodeType(application.nodeType());
        this.clusterSpecType = clusterSpecType;
        this.clusterSpecId = clusterSpecId;
        this.serviceType = serviceType;
        this.healthPort = healthPort;
    }

    @Override
    public ApplicationId getApplicationId() {
        return application.id();
    }

    @Override
    public Capacity getCapacity() {
        return capacity;
    }

    @Override
    public ClusterSpec getClusterSpecWithVersion(Version version) {
        return ClusterSpec.request(clusterSpecType, clusterSpecId).vespaVersion(version).build();
    }

    public ClusterSpec.Type getClusterSpecType() {
        return clusterSpecType;
    }

    public ClusterSpec.Id getClusterSpecId() {
        return clusterSpecId;
    }

    public ClusterId getClusterId() {
        return new ClusterId(clusterSpecId.value());
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public ApplicationInstanceId getApplicationInstanceId(Zone zone) {
        return ApplicationInstanceGenerator.toApplicationInstanceId(application.id(), zone);
    }

    public TenantId getTenantId() {
        return new TenantId(application.id().tenant().value());
    }

    public ApplicationInfo makeApplicationInfo(List<HostName> hostnames) {
        List<HostInfo> hostInfos = hostnames.stream().map(this::makeHostInfo).collect(Collectors.toList());
        return new ApplicationInfo(application.id(), 0, new HostsModel(hostInfos));
    }

    private HostInfo makeHostInfo(HostName hostname) {
        PortInfo portInfo = new PortInfo(healthPort, StateV1HealthModel.HTTP_HEALTH_PORT_TAGS);

        Map<String, String> properties = new HashMap<>();
        properties.put(ModelGenerator.CLUSTER_ID_PROPERTY_NAME, getClusterId().s());

        ServiceInfo serviceInfo = new ServiceInfo(
                // service name == service type for the first service of each type on each host
                serviceType.s(),
                serviceType.s(),
                Collections.singletonList(portInfo),
                properties,
                configIdFor(hostname).s(),
                hostname.value());

        return new HostInfo(hostname.value(), Collections.singletonList(serviceInfo));
    }

    public ConfigId configIdFor(HostName hostname) {
        // Not necessarily unique, but service monitor doesn't require it to be unique.
        return new ConfigId(String.format("%s/%s", clusterSpecId.value(), prefixTo(hostname.value(), '.')));
    }

    private static String prefixTo(String string, char sentinel) {
        int offset = string.indexOf(sentinel);
        if (offset == -1) {
            return string;
        } else {
            return string.substring(0, offset);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfraApplication that = (InfraApplication) o;
        return healthPort == that.healthPort &&
                Objects.equals(application, that.application) &&
                Objects.equals(capacity, that.capacity) &&
                clusterSpecType == that.clusterSpecType &&
                Objects.equals(clusterSpecId, that.clusterSpecId) &&
                Objects.equals(serviceType, that.serviceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, capacity, clusterSpecType, clusterSpecId, serviceType, healthPort);
    }

    @Override
    public String toString() {
        return "InfraApplication{" +
                "application=" + application +
                ", capacity=" + capacity +
                ", clusterSpecType=" + clusterSpecType +
                ", clusterSpecId=" + clusterSpecId +
                ", serviceType=" + serviceType +
                ", healthPort=" + healthPort +
                '}';
    }

}
