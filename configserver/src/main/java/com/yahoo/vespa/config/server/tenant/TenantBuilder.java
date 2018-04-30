// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.path.Path;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactoryFactory;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.application.ZKTenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.session.*;
import com.yahoo.vespa.config.server.zookeeper.SessionCounter;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Clock;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builder for helping out with tenant creation. Each of a tenants dependencies may be overridden for testing.
 *
 * @author Ulf Lilleengen
 */
public class TenantBuilder {

    private final Path tenantPath;
    private final GlobalComponentRegistry componentRegistry;
    private final TenantName tenant;
    private RemoteSessionRepo remoteSessionRepo;
    private LocalSessionRepo localSessionRepo;
    private SessionFactory sessionFactory;
    private LocalSessionLoader localSessionLoader;
    private TenantApplications applicationRepo;
    private SessionCounter sessionCounter;
    private ReloadHandler reloadHandler;
    private RequestHandler requestHandler;
    private RemoteSessionFactory remoteSessionFactory;
    private TenantFileSystemDirs tenantFileSystemDirs;
    private HostValidator<ApplicationId> hostValidator;

    private TenantBuilder(GlobalComponentRegistry componentRegistry, TenantName tenant) {
        this.componentRegistry = componentRegistry;
        this.tenantPath = TenantRepository.getTenantPath(tenant);
        this.tenant = tenant;
    }

    public static TenantBuilder create(GlobalComponentRegistry componentRegistry, TenantName tenant) {
        return new TenantBuilder(componentRegistry, tenant);
    }

    public TenantBuilder withSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        return this;
    }

    public TenantBuilder withLocalSessionRepo(LocalSessionRepo localSessionRepo) {
        this.localSessionRepo = localSessionRepo;
        return this;
    }

    public TenantBuilder withRemoteSessionRepo(RemoteSessionRepo remoteSessionRepo) {
        this.remoteSessionRepo = remoteSessionRepo;
        return this;
    }

    public TenantBuilder withApplicationRepo(TenantApplications applicationRepo) {
        this.applicationRepo = applicationRepo;
        return this;
    }

    public TenantBuilder withRequestHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        return this;
    }

    public TenantBuilder withReloadHandler(ReloadHandler reloadHandler) {
        this.reloadHandler = reloadHandler;
        return this;
    }

    /**
     * Create a real tenant from the properties given by this builder.
     *
     * @return a new {@link Tenant} instance.
     */
    public Tenant build() {
        createTenantRequestHandler();
        createApplicationRepo();
        createRemoteSessionFactory(componentRegistry.getClock());
        createRemoteSessionRepo();
        createSessionCounter();
        createServerDbDirs();
        createSessionFactory();
        createLocalSessionRepo();
        return new Tenant(tenant,
                          tenantPath,
                          sessionFactory,
                          localSessionRepo,
                          remoteSessionRepo,
                          requestHandler,
                          reloadHandler,
                          applicationRepo,
                          componentRegistry.getCurator(),
                          tenantFileSystemDirs);
    }

	private void createLocalSessionRepo() {
        if (localSessionRepo == null) {
            localSessionRepo = new LocalSessionRepo(tenantFileSystemDirs, localSessionLoader, componentRegistry.getClock(), componentRegistry.getConfigserverConfig().sessionLifetime());
        }
    }

    private void createSessionFactory() {
        if (sessionFactory == null || localSessionLoader == null) {
            SessionFactoryImpl impl = new SessionFactoryImpl(componentRegistry, sessionCounter,
                                                             applicationRepo, tenantFileSystemDirs, hostValidator, tenant);
            if (sessionFactory == null) {
                sessionFactory = impl;
            }
            if (localSessionLoader == null) {
                localSessionLoader = impl;
            }
        }
    }

    private void createApplicationRepo() {
        if (applicationRepo == null) {
            applicationRepo = ZKTenantApplications.create(componentRegistry.getCurator(), reloadHandler, tenant);
        }
    }

    private void createSessionCounter() {
        if (sessionCounter == null) {
            sessionCounter = new SessionCounter(componentRegistry.getConfigCurator(), tenant);
        }
    }

    private void createTenantRequestHandler() {
        if (requestHandler == null || reloadHandler == null) {
            TenantRequestHandler impl = new TenantRequestHandler(componentRegistry.getMetrics(),
                                                                 tenant,
                                                                 Collections.singletonList(componentRegistry.getReloadListener()),
                                                                 ConfigResponseFactoryFactory.createFactory(componentRegistry.getConfigserverConfig()),
                                                                 componentRegistry.getHostRegistries());
            if (hostValidator == null) {
                this.hostValidator = impl;
            }
            if (requestHandler == null) {
                requestHandler = impl;
            }
            if (reloadHandler == null) {
                reloadHandler = impl;
            }
        }
    }

    private void createRemoteSessionFactory(Clock clock) {
        if (remoteSessionFactory == null) {
            remoteSessionFactory = new RemoteSessionFactory(componentRegistry, tenant, clock);
        }
    }

    private void createRemoteSessionRepo() {
        if (remoteSessionRepo == null) {
            remoteSessionRepo = new RemoteSessionRepo(componentRegistry.getCurator(),
                    remoteSessionFactory,
                    reloadHandler,
                    tenant,
                    applicationRepo,
                    componentRegistry.getMetrics().getOrCreateMetricUpdater(Metrics.createDimensions(tenant)),
                    // TODO: Check if we can avoid using one executor service per tenant. Either one for all
                    // or have a few executors and hash on tenant name to find out which one to use here
                    createSingleThreadedExecutorService(RemoteSessionRepo.class.getName()));
        }
    }

    private ExecutorService createSingleThreadedExecutorService(String executorName) {
        return Executors.newSingleThreadExecutor(ThreadFactoryFactory.getThreadFactory(executorName + "-" + tenant.value()));
    }

    private void createServerDbDirs() {
        if (tenantFileSystemDirs == null) {
            tenantFileSystemDirs = new TenantFileSystemDirs(new File(
                    Defaults.getDefaults().underVespaHome(componentRegistry
                                                                  .getServerDB()
                                                                  .getConfigserverConfig()
                                                                  .configServerDBDir())),
                                                            tenant);
        }
    }

    public TenantApplications getApplicationRepo() {
        return applicationRepo;
    }

    public TenantName getTenantName() { return tenant; }
}
