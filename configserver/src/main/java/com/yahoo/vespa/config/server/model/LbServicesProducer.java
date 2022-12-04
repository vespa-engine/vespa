// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ApplicationClusterInfo;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.FlagSource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;

/**
 * Produces lb-services cfg
 *
 * @author Vegard Havdal
 */
public class LbServicesProducer implements LbServicesConfig.Producer {

    private final Map<TenantName, Set<ApplicationInfo>> models;
    private final Zone zone;

    public LbServicesProducer(Map<TenantName, Set<ApplicationInfo>> models, Zone zone, FlagSource flagSource) {
        this.models = models;
        this.zone = zone;
    }

    @Override
    public void getConfig(LbServicesConfig.Builder builder) {
        models.keySet().stream()
                .sorted()
                .forEach(tenant -> {
            builder.tenants(tenant.value(), getTenantConfig(models.get(tenant)));
        });
    }

    private LbServicesConfig.Tenants.Builder getTenantConfig(Set<ApplicationInfo> apps) {
        LbServicesConfig.Tenants.Builder tb = new LbServicesConfig.Tenants.Builder();
        apps.stream()
                .sorted(Comparator.comparing(ApplicationInfo::getApplicationId))
                .filter(applicationInfo -> generateRoutingConfig(applicationInfo.getApplicationId()))
                .forEach(applicationInfo -> tb.applications(createLbAppIdKey(applicationInfo.getApplicationId()), getAppConfig(applicationInfo)));
        return tb;
    }

    private boolean generateRoutingConfig(ApplicationId applicationId) {
        return ( ! applicationId.instance().isTester());
    }

    private String createLbAppIdKey(ApplicationId applicationId) {
        return applicationId.application().value() + ":" + zone.environment().value() + ":" + zone.region().value() + ":" + applicationId.instance().value();
    }

    private LbServicesConfig.Tenants.Applications.Builder getAppConfig(ApplicationInfo app) {
        LbServicesConfig.Tenants.Applications.Builder ab = new LbServicesConfig.Tenants.Applications.Builder();

        // TODO: read active rotation from ApplicationClusterInfo
        ab.activeRotation(getActiveRotation(app));

        Set<ApplicationClusterInfo> applicationClusterInfos = app.getModel().applicationClusterInfo();
        List<LbServicesConfig.Tenants.Applications.Endpoints.Builder> endpointBuilder = applicationClusterInfos.stream()
                .sorted(Comparator.comparing(ApplicationClusterInfo::name))
                .map(ApplicationClusterInfo::endpoints)
                .flatMap(endpoints -> getEndpointConfig(endpoints).stream())
                .collect(Collectors.toList());
        ab.endpoints(endpointBuilder);
        return ab;
    }

    private List<LbServicesConfig.Tenants.Applications.Endpoints.Builder> getEndpointConfig(List<ApplicationClusterEndpoint> clusterEndpoints) {
        return clusterEndpoints.stream()
                .sorted(Comparator.comparing(ApplicationClusterEndpoint::dnsName))
                .map(this::getEndpointConfig)
                .collect(Collectors.toList());
    }

    private LbServicesConfig.Tenants.Applications.Endpoints.Builder getEndpointConfig(ApplicationClusterEndpoint clusterEndpoints) {
        LbServicesConfig.Tenants.Applications.Endpoints.Builder builder = new LbServicesConfig.Tenants.Applications.Endpoints.Builder();
        return builder.dnsName(clusterEndpoints.dnsName().value())
                .scope(LbServicesConfig.Tenants.Applications.Endpoints.Scope.Enum.valueOf(clusterEndpoints.scope().name()))
                .routingMethod(LbServicesConfig.Tenants.Applications.Endpoints.RoutingMethod.Enum.valueOf(clusterEndpoints.routingMethod().name()))
                .weight(clusterEndpoints.weight())
                .hosts(clusterEndpoints.hostNames())
                .clusterId(clusterEndpoints.clusterId());
    }

    private boolean getActiveRotation(ApplicationInfo app) {
        boolean activeRotation = false;
        for (HostInfo hostInfo : app.getModel().getHosts()) {
            Optional<ServiceInfo> container = hostInfo.getServices().stream().filter(
                    serviceInfo -> serviceInfo.getServiceType().equals(CONTAINER.serviceName) ||
                                   serviceInfo.getServiceType().equals(QRSERVER.serviceName)).
                    findAny();
            if (container.isPresent()) {
                activeRotation |= Boolean.parseBoolean(container.get().getProperty("activeRotation").orElse("false"));
            }
        }
        return activeRotation;
    }

}
