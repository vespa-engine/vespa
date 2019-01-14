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
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hakonhall
 */
public class DuperModelManager implements DuperModelInfraApi {
    private static Logger logger = Logger.getLogger(DuperModelManager.class.getName());

    // Infrastructure applications
    private final ConfigServerHostApplication configServerHostApplication = new ConfigServerHostApplication();
    private final ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
    private final ControllerApplication controllerApplication = new ControllerApplication();
    private final ControllerHostApplication controllerHostApplication = new ControllerHostApplication();
    private final ConfigServerApplication configServerApplication = new ConfigServerApplication();

    private final Map<ApplicationId, InfraApplication> supportedInfraApplications = Stream.of(
            configServerApplication,
            configServerHostApplication,
            proxyHostApplication,
            controllerApplication,
            controllerHostApplication)
            .collect(Collectors.toMap(InfraApplication::getApplicationId, Function.identity()));

    private final boolean multitenant;

    private final Object monitor = new Object();
    private final DuperModel duperModel;
    // The set of active infrastructure ApplicationInfo. Not all are necessarily in the DuperModel for historical reasons.
    private final Set<ApplicationId> activeInfraInfos = new HashSet<>(2 * supportedInfraApplications.size());

    @Inject
    public DuperModelManager(ConfigserverConfig configServerConfig, FlagSource flagSource, SuperModelProvider superModelProvider) {
        this(configServerConfig.multitenant(), superModelProvider, new DuperModel());
    }

    /** For testing */
    public DuperModelManager(boolean multitenant, SuperModelProvider superModelProvider, DuperModel duperModel) {
        this.multitenant = multitenant;
        this.duperModel = duperModel;

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

    /**
     * Returns true if application is considered an infrastructure application by the DuperModel.
     *
     * <p>Note: The tenant host "application" is NOT considered an infrastructure application: It is just a
     * cluster in the {@link ZoneApplication zone application}.
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
            if (infraApplicationBelongsInDuperModel(applicationId)) {
                duperModel.add(application.makeApplicationInfo(hostnames));
            }
        }
    }

    @Override
    public void infraApplicationRemoved(ApplicationId applicationId) {
        synchronized (monitor) {
            activeInfraInfos.remove(applicationId);
            if (infraApplicationBelongsInDuperModel(applicationId)) {
                duperModel.remove(applicationId);
            }
        }
    }

    public List<ApplicationInfo> getApplicationInfos() {
        synchronized (monitor) {
            return duperModel.getApplicationInfos();
        }
    }

    private boolean infraApplicationBelongsInDuperModel(ApplicationId applicationId) {
        // At most 1 of the config server and controller applications can be in the duper model.
        // The problem of allowing more than 1 is that orchestration will fail since hostname -> application lookup
        // will not be unique.
        if (applicationId.equals(controllerApplication.getApplicationId())) {
            if (!multitenant) return false;
            if (duperModel.contains(configServerApplication.getApplicationId())) {
                logger.log(LogLevel.ERROR, "Refusing to add controller application to duper model " +
                        "since it already contains config server");
                return false;
            }
            return true;
        } else if (applicationId.equals(configServerApplication.getApplicationId())) {
            if (!multitenant) return false;
            if (duperModel.contains(controllerApplication.getApplicationId())) {
                logger.log(LogLevel.ERROR, "Refusing to add config server application to duper model " +
                        "since it already contains controller");
                return false;
            }
            return true;
        } else {
            return true;
        }
    }
}
