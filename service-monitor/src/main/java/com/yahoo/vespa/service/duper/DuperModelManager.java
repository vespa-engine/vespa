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
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.DuperModelListener;
import com.yahoo.vespa.service.monitor.DuperModelProvider;
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
public class DuperModelManager implements DuperModelProvider, DuperModelInfraApi {

    // Infrastructure applications
    static final ControllerHostApplication controllerHostApplication = new ControllerHostApplication();
    static final ControllerApplication controllerApplication = new ControllerApplication();
    static final ConfigServerHostApplication configServerHostApplication = new ConfigServerHostApplication();
    static final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    static final ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
    static final TenantHostApplication tenantHostApplication = new TenantHostApplication();
    static final DevHostApplication devHostApplicaton = new DevHostApplication();

    private final Map<ApplicationId, InfraApplication> supportedInfraApplications;

    private final Object monitor = new Object();
    private final DuperModel duperModel;
    // The set of active infrastructure ApplicationInfo. Not all are necessarily in the DuperModel for historical reasons.
    private final Set<ApplicationId> activeInfraInfos = new HashSet<>(10);

    private boolean superModelIsComplete = false;
    private boolean infraApplicationsIsComplete = false;

    @Inject
    public DuperModelManager(ConfigserverConfig configServerConfig, FlagSource flagSource, SuperModelProvider superModelProvider) {
        this(configServerConfig.multitenant(),
                configServerConfig.serverNodeType() == ConfigserverConfig.ServerNodeType.Enum.controller,
             superModelProvider, new DuperModel(), flagSource, SystemName.from(configServerConfig.system()));
    }

    /** Non-private for testing */
    DuperModelManager(boolean multitenant, boolean isController, SuperModelProvider superModelProvider, DuperModel duperModel, FlagSource flagSource, SystemName system) {
        this.duperModel = duperModel;

        if (system == SystemName.dev) {
            // TODO (mortent): Support controllerApplication in dev system
            supportedInfraApplications =
                    Stream.of(devHostApplicaton, configServerApplication)
                    .collect(Collectors.toUnmodifiableMap(InfraApplication::getApplicationId, Function.identity()));
        } else if (multitenant) {
            supportedInfraApplications =
                    (isController ?
                            Stream.of(controllerHostApplication, controllerApplication) :
                            Stream.of(configServerHostApplication, configServerApplication, proxyHostApplication, tenantHostApplication)
                    ).collect(Collectors.toUnmodifiableMap(InfraApplication::getApplicationId, Function.identity()));
        } else {
            supportedInfraApplications = Map.of();
        }

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

            @Override
            public void notifyOfCompleteness(SuperModel superModel) {
                synchronized (monitor) {
                    if (!superModelIsComplete) {
                        superModelIsComplete = true;
                        maybeSetDuperModelAsComplete();
                    }
                }
            }
        });
    }

    /**
     * Synchronously call {@link DuperModelListener#applicationActivated(ApplicationInfo) listener.applicationActivated()}
     * for each currently active application, and forward future changes.
     */
    @Override
    public void registerListener(DuperModelListener listener) {
        synchronized (monitor) {
            duperModel.registerListener(listener);
        }
    }

    @Override
    public List<InfraApplicationApi> getSupportedInfraApplications() {
        return new ArrayList<>(supportedInfraApplications.values());
    }

    @Override
    public Optional<InfraApplicationApi> getInfraApplication(ApplicationId applicationId) {
        return Optional.ofNullable(supportedInfraApplications.get(applicationId));
    }

    /**
     * Returns true if application is considered an infrastructure application by the DuperModel.
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

    @Override
    public void infraApplicationsIsNowComplete() {
        synchronized (monitor) {
            if (!infraApplicationsIsComplete) {
                infraApplicationsIsComplete = true;
                maybeSetDuperModelAsComplete();
            }
        }
    }

    public Optional<ApplicationInfo> getApplicationInfo(ApplicationId applicationId) {
        synchronized (monitor) {
            return duperModel.getApplicationInfo(applicationId);
        }
    }

    public List<ApplicationInfo> getApplicationInfos() {
        synchronized (monitor) {
            return duperModel.getApplicationInfos();
        }
    }

    private void maybeSetDuperModelAsComplete() {
        if (superModelIsComplete && infraApplicationsIsComplete) {
            duperModel.setCompleteness(true);
        }
    }
}
