// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.flags.FeatureFlag;
import com.yahoo.vespa.flags.FlagSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code DuperModel} unites the {@link com.yahoo.config.model.api.SuperModel SuperModel}
 * with synthetic applications like the config server application.
 *
 * @author hakon
 */
public class DuperModel implements DuperModelInfraApi {
    // Infrastructure applications
    private static final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    private static final ConfigServerHostApplication configServerHostApplication = new ConfigServerHostApplication();
    private static final ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
    private static final ControllerApplication controllerApplication = new ControllerApplication();
    private static final ControllerHostApplication controllerHostApplication = new ControllerHostApplication();

    private static final Map<ApplicationId, InfraApplication> supportedInfraApplications = Stream.of(
            configServerApplication,
            configServerHostApplication,
            proxyHostApplication,
            controllerApplication,
            controllerHostApplication)
            .collect(Collectors.toMap(InfraApplication::getApplicationId, Function.identity()));

    private final FeatureFlag containsInfra;
    private final FeatureFlag useConfigserverConfig;
    private final boolean multitenant;

    // Each of the above infrastructure applications may be active, in case their ApplicationInfo is present here
    private final ConcurrentHashMap<ApplicationId, ApplicationInfo> infraInfos =
            new ConcurrentHashMap<>(2 * supportedInfraApplications.size());

    private final ApplicationInfo configServerApplicationFromConfig;

    @Inject
    public DuperModel(ConfigserverConfig configServerConfig, FlagSource flagSource) {
        this(
                // Whether to include activate infrastructure applications (except from controller/config apps - see below).
                new FeatureFlag("dupermodel-contains-infra", true, flagSource),

                // For historical reasons, the ApplicationInfo in the DuperModel for controllers and config servers
                // is based on the ConfigserverConfig (this flag is true). We want to transition to use the
                // infrastructure application activated by the InfrastructureProvisioner once that supports health.
                new FeatureFlag("dupermodel-use-configserverconfig", true, flagSource),
                configServerConfig.multitenant(),
                configServerApplication.makeApplicationInfoFromConfig(configServerConfig));
    }

    /** For testing */
    public DuperModel(FeatureFlag containsInfra,
                      FeatureFlag useConfigserverConfig,
                      boolean multitenant,
                      ApplicationInfo configServerApplicationInfo) {
        this.containsInfra = containsInfra;
        this.useConfigserverConfig = useConfigserverConfig;
        this.multitenant = multitenant;
        this.configServerApplicationFromConfig = configServerApplicationInfo;
    }

    public ConfigServerApplication getConfigServerApplication() {
        return configServerApplication;
    }

    public ConfigServerHostApplication getConfigServerHostApplication() {
        return configServerHostApplication;
    }

    public ProxyHostApplication getProxyHostApplication() {
        return proxyHostApplication;
    }

    public ControllerApplication getControllerApplication() {
        return controllerApplication;
    }

    public ControllerHostApplication getControllerHostApplication() {
        return controllerHostApplication;
    }

    @Override
    public List<InfraApplicationApi> getSupportedInfraApplications() {
        return new ArrayList<>(supportedInfraApplications.values());
    }

    @Override
    public boolean infraApplicationIsActive(ApplicationId applicationId) {
        return infraInfos.containsKey(applicationId);
    }

    @Override
    public void infraApplicationActivated(ApplicationId applicationId, List<HostName> hostnames) {
        InfraApplication application = supportedInfraApplications.get(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("There is no infrastructure application with ID '" + applicationId + "'");
        }

        infraInfos.put(application.getApplicationId(), application.makeApplicationInfo(hostnames));
    }

    @Override
    public void infraApplicationRemoved(ApplicationId applicationId) {
        infraInfos.remove(applicationId);
    }

    public List<ApplicationInfo> getApplicationInfos(SuperModel superModelSnapshot) {
        List<ApplicationInfo> infos = new ArrayList<>();

        // Single-tenant applications have the config server as part of the application model.
        if (multitenant) {
            if (useConfigserverConfig.value()) {
                infos.add(configServerApplicationFromConfig);
            } else {
                ApplicationInfo info = infraInfos.get(controllerApplication.getApplicationId());
                if (info == null) {
                    info = infraInfos.get(configServerApplication.getApplicationId());
                }

                if (info != null) {
                    infos.add(info);
                }
            }
        }

        if (containsInfra.value()) {
            // All infra apps, excluding controller and config server which handled above.
            infraInfos.values().stream()
                    .filter(info -> !info.getApplicationId().equals(controllerApplication.getApplicationId()) &&
                            !info.getApplicationId().equals(configServerApplication.getApplicationId()))
                    .forEach(infos::add);
        }

        infos.addAll(superModelSnapshot.getAllApplicationInfos());
        return infos;
    }
}
