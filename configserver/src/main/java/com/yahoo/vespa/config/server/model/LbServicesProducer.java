// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.google.common.base.Joiner;
import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final BooleanFlag usePowerOfTwoChoicesLb;
    private final BooleanFlag generateNonMtlsEndpoint;

    public LbServicesProducer(Map<TenantName, Set<ApplicationInfo>> models, Zone zone, FlagSource flagSource) {
        this.models = models;
        this.zone = zone;
        usePowerOfTwoChoicesLb = Flags.USE_POWER_OF_TWO_CHOICES_LOAD_BALANCING.bindTo(flagSource);
        generateNonMtlsEndpoint = Flags.GENERATE_NON_MTLS_ENDPOINT.bindTo(flagSource);
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
        ab.activeRotation(getActiveRotation(app));
        ab.usePowerOfTwoChoicesLb(usePowerOfTwoChoicesLb(app));
        ab.generateNonMtlsEndpoint(generateNonMtlsEndpoint(app));
        app.getModel().getHosts().stream()
                .sorted((a, b) -> a.getHostname().compareTo(b.getHostname()))
                .forEach(hostInfo -> ab.hosts(hostInfo.getHostname(), getHostsConfig(hostInfo)));
        return ab;
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

    private boolean usePowerOfTwoChoicesLb(ApplicationInfo app) {
        return usePowerOfTwoChoicesLb.with(FetchVector.Dimension.APPLICATION_ID, app.getApplicationId().serializedForm()).value();
    }

    private boolean generateNonMtlsEndpoint(ApplicationInfo app) {
        return generateNonMtlsEndpoint.with(FetchVector.Dimension.APPLICATION_ID, app.getApplicationId().serializedForm()).value();
    }

    private LbServicesConfig.Tenants.Applications.Hosts.Builder getHostsConfig(HostInfo hostInfo) {
        LbServicesConfig.Tenants.Applications.Hosts.Builder hb = new LbServicesConfig.Tenants.Applications.Hosts.Builder();
        hb.hostname(hostInfo.getHostname());
        hostInfo.getServices().forEach(serviceInfo -> hb.services(serviceInfo.getServiceName(), getServiceConfig(serviceInfo)));
        return hb;
    }

    private LbServicesConfig.Tenants.Applications.Hosts.Services.Builder getServiceConfig(ServiceInfo serviceInfo) {
        List<String> endpointAliases = Stream.of(serviceInfo.getProperty("endpointaliases").orElse("").split(",")).
                filter(prop -> !"".equals(prop)).collect(Collectors.toList());
        endpointAliases.addAll(Stream.of(serviceInfo.getProperty("rotations").orElse("").split(",")).filter(prop -> !"".equals(prop)).collect(Collectors.toList()));
        Collections.sort(endpointAliases);

        LbServicesConfig.Tenants.Applications.Hosts.Services.Builder sb = new LbServicesConfig.Tenants.Applications.Hosts.Services.Builder()
                .type(serviceInfo.getServiceType())
                .clustertype(serviceInfo.getProperty("clustertype").orElse(""))
                .clustername(serviceInfo.getProperty("clustername").orElse(""))
                .configId(serviceInfo.getConfigId())
                .servicealiases(Stream.of(serviceInfo.getProperty("servicealiases").orElse("").split(",")).
                                filter(prop -> !"".equals(prop)).sorted((a, b) -> a.compareTo(b)).collect(Collectors.toList()))
                .endpointaliases(endpointAliases)
                .index(Integer.parseInt(serviceInfo.getProperty("index").orElse("999999")));
        serviceInfo.getPorts().forEach(portInfo -> {
            LbServicesConfig.Tenants.Applications.Hosts.Services.Ports.Builder pb = new LbServicesConfig.Tenants.Applications.Hosts.Services.Ports.Builder()
                    .number(portInfo.getPort())
                    .tags(Joiner.on(" ").join(portInfo.getTags()));
            sb.ports(pb);
                });
        return sb;
    }
}
