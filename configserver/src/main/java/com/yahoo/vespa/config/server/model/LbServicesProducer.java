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

import java.util.Arrays;
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
    private final BooleanFlag useHttpsLoadBalancerUpstream;

    public LbServicesProducer(Map<TenantName, Set<ApplicationInfo>> models, Zone zone, FlagSource flagSource) {
        this.models = models;
        this.zone = zone;
        this.useHttpsLoadBalancerUpstream = Flags.USE_HTTPS_LOAD_BALANCER_UPSTREAM.bindTo(flagSource);
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
                .forEach(applicationInfo -> tb.applications(createLbAppIdKey(applicationInfo.getApplicationId()), getAppConfig(applicationInfo)));
        return tb;
    }

    private String createLbAppIdKey(ApplicationId applicationId) {
        return applicationId.application().value() + ":" + zone.environment().value() + ":" + zone.region().value() + ":" + applicationId.instance().value();
    }

    private LbServicesConfig.Tenants.Applications.Builder getAppConfig(ApplicationInfo app) {
        LbServicesConfig.Tenants.Applications.Builder ab = new LbServicesConfig.Tenants.Applications.Builder();
        ab.activeRotation(getActiveRotation(app));
        ab.upstreamHttps(useHttpsLoadBalancerUpstream(app));
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
                activeRotation |= Boolean.valueOf(container.get().getProperty("activeRotation").orElse("false"));
            }
        }
        return activeRotation;
    }

    private boolean useHttpsLoadBalancerUpstream(ApplicationInfo app) {
        return useHttpsLoadBalancerUpstream.with(FetchVector.Dimension.APPLICATION_ID, app.getApplicationId().serializedForm()).value();
    }

    private LbServicesConfig.Tenants.Applications.Hosts.Builder getHostsConfig(HostInfo hostInfo) {
        var hostsBuilder = new LbServicesConfig.Tenants.Applications.Hosts.Builder()
                .hostname(hostInfo.getHostname());

        hostInfo.getServices().forEach(serviceInfo -> {
            hostsBuilder.services(serviceInfo.getServiceName(), getServiceConfig(serviceInfo));
        });

        return hostsBuilder;
    }

    private LbServicesConfig.Tenants.Applications.Hosts.Services.Builder getServiceConfig(ServiceInfo serviceInfo) {
        var endpointAliases = serviceInfo.getProperty("endpointaliases").stream()
                .flatMap(value -> Arrays.stream(value.split(",")));

        var rotations = serviceInfo.getProperty("rotations").stream()
                .flatMap(value -> Arrays.stream(value.split(",")));

        var aliasesAndRotations = Stream.concat(endpointAliases, rotations)
                .filter(value -> ! value.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        var serviceAliases = serviceInfo.getProperty("servicealiases").stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .filter(value -> ! value.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        var servicesBuilder = new LbServicesConfig.Tenants.Applications.Hosts.Services.Builder()
                .type(serviceInfo.getServiceType())
                .clustertype(serviceInfo.getProperty("clustertype").orElse(""))
                .clustername(serviceInfo.getProperty("clustername").orElse(""))
                .configId(serviceInfo.getConfigId())
                .servicealiases(serviceAliases)
                .endpointaliases(aliasesAndRotations)
                .index(Integer.parseInt(serviceInfo.getProperty("index").orElse("999999")));

        serviceInfo.getPorts().forEach(portInfo -> {
            var portsBuilder = new LbServicesConfig.Tenants.Applications.Hosts.Services.Ports.Builder()
                    .number(portInfo.getPort())
                    .tags(Joiner.on(" ").join(portInfo.getTags()));
            servicesBuilder.ports(portsBuilder);
        });

        return servicesBuilder;
    }
}
