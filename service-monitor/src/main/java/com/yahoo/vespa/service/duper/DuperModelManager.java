// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.service.monitor.CriticalRegion;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hakonhall
 */
public class DuperModelManager implements DuperModelProvider, DuperModelInfraApi {

    private static final Logger logger = Logger.getLogger(DuperModelManager.class.getName());

    // Infrastructure applications
    static final ControllerHostApplication controllerHostApplication = new ControllerHostApplication();
    static final ControllerApplication controllerApplication = new ControllerApplication();
    static final ConfigServerHostApplication configServerHostApplication = new ConfigServerHostApplication();
    static final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    static final ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
    static final TenantHostApplication tenantHostApplication = new TenantHostApplication();

    private final Map<ApplicationId, InfraApplication> supportedInfraApplications;

    private static final CriticalRegionChecker disallowedDuperModeLockAcquisitionRegions =
            new CriticalRegionChecker("duper model deadlock detection");

    private final ReentrantLock lock = new ReentrantLock(true);
    private final DuperModel duperModel;
    // The set of active infrastructure ApplicationInfo. Not all are necessarily in the DuperModel for historical reasons.
    private final Set<ApplicationId> activeInfraInfos = new HashSet<>(10);

    private boolean superModelIsComplete = false;
    private boolean infraApplicationsIsComplete = false;

    @Inject
    public DuperModelManager(ConfigserverConfig configServerConfig, SuperModelProvider superModelProvider) {
        this(configServerConfig.multitenant(),
                configServerConfig.serverNodeType() == ConfigserverConfig.ServerNodeType.Enum.controller,
             superModelProvider, new DuperModel());
    }

    /** Non-private for testing */
    public DuperModelManager(boolean multitenant, boolean isController, SuperModelProvider superModelProvider,
                             DuperModel duperModel) {
        this.duperModel = duperModel;

        if (multitenant) {
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
                lockedRunnable(() -> duperModel.add(application));
            }

            @Override
            public void applicationRemoved(SuperModel superModel, ApplicationId applicationId) {
                lockedRunnable(() -> duperModel.remove(applicationId));
            }

            @Override
            public void notifyOfCompleteness(SuperModel superModel) {
                lockedRunnable(() -> {
                    if (!superModelIsComplete) {
                        superModelIsComplete = true;
                        logger.log(Level.FINE, "All bootstrap tenant applications have been activated");
                        maybeSetDuperModelAsComplete();
                    }
                });
            }
        });
    }

    /**
     * Synchronously call {@link DuperModelListener#applicationActivated(ApplicationInfo) listener.applicationActivated()}
     * for each currently active application, and forward future changes.
     */
    @Override
    public void registerListener(DuperModelListener listener) {
        lockedRunnable(() -> duperModel.registerListener(listener));
    }

    @Override
    public List<InfraApplicationApi> getSupportedInfraApplications() {
        return new ArrayList<>(supportedInfraApplications.values());
    }

    @Override
    public Optional<InfraApplicationApi> getInfraApplication(ApplicationId applicationId) {
        return Optional.ofNullable(supportedInfraApplications.get(applicationId));
    }

    /** Returns true if application is considered an infrastructure application by the DuperModel. */
    public boolean isSupportedInfraApplication(ApplicationId applicationId) {
        return supportedInfraApplications.containsKey(applicationId);
    }

    @Override
    public boolean infraApplicationIsActive(ApplicationId applicationId) {
        return lockedSupplier(() -> activeInfraInfos.contains(applicationId));
    }

    @Override
    public void infraApplicationActivated(ApplicationId applicationId, List<HostName> hostnames) {
        InfraApplication application = supportedInfraApplications.get(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("There is no infrastructure application with ID '" + applicationId + "'");
        }

        lockedRunnable(() -> {
            activeInfraInfos.add(applicationId);
            duperModel.add(application.makeApplicationInfo(hostnames));
        });
    }

    @Override
    public void infraApplicationRemoved(ApplicationId applicationId) {
        if (!supportedInfraApplications.containsKey(applicationId)) {
            throw new IllegalArgumentException("There is no infrastructure application with ID '" + applicationId + "'");
        }

        lockedRunnable(() -> {
            activeInfraInfos.remove(applicationId);
            duperModel.remove(applicationId);
        });
    }

    @Override
    public void infraApplicationsIsNowComplete() {
        lockedRunnable(() -> {
            if (!infraApplicationsIsComplete) {
                infraApplicationsIsComplete = true;
                logger.log(Level.INFO, "All infrastructure applications have been activated");
                maybeSetDuperModelAsComplete();
            }
        });
    }

    public Optional<ApplicationInfo> getApplicationInfo(ApplicationId applicationId) {
        return lockedSupplier(() -> duperModel.getApplicationInfo(applicationId));
    }

    public Optional<ApplicationInfo> getApplicationInfo(HostName hostname) {
        return lockedSupplier(() -> duperModel.getApplicationInfo(hostname));
    }

    public List<ApplicationInfo> getApplicationInfos() {
        return lockedSupplier(() -> duperModel.getApplicationInfos());
    }

    /**
     * Within the region, trying to accesss the duper model (without already having the lock)
     * will cause an IllegalStateException to be thrown.
     */
    public CriticalRegion disallowDuperModelLockAcquisition(String regionDescription) {
        return disallowedDuperModeLockAcquisitionRegions.startCriticalRegion(regionDescription);
    }

    private void maybeSetDuperModelAsComplete() {
        if (superModelIsComplete && infraApplicationsIsComplete) {
            duperModel.setComplete();
        }
    }

    private <T> T lockedSupplier(Supplier<T> supplier) {
        if (lock.isHeldByCurrentThread()) {
            return supplier.get();
        }

        disallowedDuperModeLockAcquisitionRegions
                .assertOutsideCriticalRegions("acquiring duper model lock");

        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    private void lockedRunnable(Runnable runnable) {
        if (lock.isHeldByCurrentThread()) {
            runnable.run();
        }

        disallowedDuperModeLockAcquisitionRegions
                .assertOutsideCriticalRegions("acquiring duper model lock");

        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

}
