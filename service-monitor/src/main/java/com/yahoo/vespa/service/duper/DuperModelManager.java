// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hakonhall
 */
public class DuperModelManager implements DuperModelInfraApi {

    // Infrastructure applications
    static final ControllerHostApplication controllerHostApplication = new ControllerHostApplication();
    static final ControllerApplication controllerApplication = new ControllerApplication();
    static final ConfigServerHostApplication configServerHostApplication = new ConfigServerHostApplication();
    static final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    static final ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
    static final TenantHostApplication tenantHostApplication = new TenantHostApplication();

    private final Map<ApplicationId, InfraApplication> supportedInfraApplications;
    private final Map<ApplicationId, InfraApplication> supportedMinusTenantHostInfraApplications;

    private final Object monitor = new Object();
    private final DuperModel duperModel;
    // The set of active infrastructure ApplicationInfo. Not all are necessarily in the DuperModel for historical reasons.
    private final Set<ApplicationId> activeInfraInfos = new HashSet<>(10);

    private final BooleanFlag tenantHostApplicationEnabled;

    @Inject
    public DuperModelManager(ConfigserverConfig configServerConfig, FlagSource flagSource, SuperModelProvider superModelProvider) {
        this(configServerConfig.multitenant(),
                configServerConfig.serverNodeType() == ConfigserverConfig.ServerNodeType.Enum.controller,
                superModelProvider, new DuperModel(), flagSource);
    }

    /** For testing */
    DuperModelManager(boolean multitenant, boolean isController, SuperModelProvider superModelProvider, DuperModel duperModel, FlagSource flagSource) {
        this.duperModel = duperModel;
        this.tenantHostApplicationEnabled = Flags.ENABLE_TENANT_HOST_APP.bindTo(flagSource);

        if (multitenant) {
            supportedInfraApplications =
                    (isController ?
                            Stream.of(controllerHostApplication, controllerApplication) :
                            Stream.of(configServerHostApplication, configServerApplication, proxyHostApplication, tenantHostApplication)
                    ).collect(Collectors.toUnmodifiableMap(InfraApplication::getApplicationId, Function.identity()));
        } else {
            supportedInfraApplications = Map.of();
        }
        supportedMinusTenantHostInfraApplications = supportedInfraApplications.entrySet().stream()
                .filter(app -> app.getValue().getCapacity().type() != NodeType.host)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        superModelProvider.registerListener(new SuperModelListener() {
            @Override
            public void applicationActivated(SuperModel superModel, ApplicationInfo application) {
                synchronized (monitor) {
                    duperModel.add(application);
                }
            }

            @Override
            public void applicationRemoved(SuperModel superModel, ApplicationId applicationId) {
                synchronized (monitor) {
                    duperModel.remove(applicationId);
                }
            }
        });
    }

    /**
     * Synchronously call {@link DuperModelListener#applicationActivated(ApplicationInfo) listener.applicationActivated()}
     * for each currently active application, and forward future changes.
     */
    public void registerListener(DuperModelListener listener) {
        synchronized (monitor) {
            duperModel.registerListener(listener);
        }
    }

    @Override
    public List<InfraApplicationApi> getSupportedInfraApplications() {
        return new ArrayList<>(getSupportedApps().values());
    }

    @Override
    public Optional<InfraApplicationApi> getInfraApplication(ApplicationId applicationId) {
        return Optional.ofNullable(getSupportedApps().get(applicationId));
    }

    private Map<ApplicationId, InfraApplication> getSupportedApps() {
        return tenantHostApplicationEnabled.value() ? supportedInfraApplications : supportedMinusTenantHostInfraApplications;
    }

    /**
     * Returns true if application is considered an infrastructure application by the DuperModel.
     *
     * <p>Note: Unless enable-tenant-host-app flag is enabled, the tenant host "application" is NOT considered an
     * infrastructure application: It is just a cluster in the {@link ZoneApplication zone application}.
     */
    public boolean isSupportedInfraApplication(ApplicationId applicationId) {
        return supportedInfraApplications.containsKey(applicationId);
    }

    @Override
    public boolean infraApplicationIsActive(ApplicationId applicationId) {
        synchronized (monitor) {
            return activeInfraInfos.contains(applicationId);
        }
    }

    @Override
    public void infraApplicationActivated(ApplicationId applicationId, List<HostName> hostnames) {
        InfraApplication application = supportedInfraApplications.get(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("There is no infrastructure application with ID '" + applicationId + "'");
        }

        synchronized (monitor) {
            activeInfraInfos.add(applicationId);
            duperModel.add(application.makeApplicationInfo(hostnames));
        }
    }

    @Override
    public void infraApplicationRemoved(ApplicationId applicationId) {
        if (!supportedInfraApplications.containsKey(applicationId)) {
            throw new IllegalArgumentException("There is no infrastructure application with ID '" + applicationId + "'");
        }

        synchronized (monitor) {
            activeInfraInfos.remove(applicationId);
            duperModel.remove(applicationId);
        }
    }

    public List<ApplicationInfo> getApplicationInfos() {
        synchronized (monitor) {
            return duperModel.getApplicationInfos();
        }
    }
}
