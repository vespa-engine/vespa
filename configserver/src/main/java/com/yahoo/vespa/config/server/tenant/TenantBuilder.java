// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.session.*;

import java.util.Collections;

/**
 * Builder for helping out with tenant creation. Each of a tenants dependencies may be overridden for testing.
 *
 * @author Ulf Lilleengen
 */
public class TenantBuilder {

    private final GlobalComponentRegistry componentRegistry;
    private final TenantName tenant;
    private RemoteSessionRepo remoteSessionRepo;
    private LocalSessionRepo localSessionRepo;
    private SessionFactory sessionFactory;
    private LocalSessionLoader localSessionLoader;
    private TenantApplications applicationRepo;
    private TenantRequestHandler reloadHandler;
    private RequestHandler requestHandler;
    private RemoteSessionFactory remoteSessionFactory;
    private HostValidator<ApplicationId> hostValidator;

    private TenantBuilder(GlobalComponentRegistry componentRegistry, TenantName tenant) {
        this.componentRegistry = componentRegistry;
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

    public TenantBuilder withApplicationRepo(TenantApplications applicationRepo) {
        this.applicationRepo = applicationRepo;
        return this;
    }

    public TenantBuilder withRequestHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
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
        createRemoteSessionFactory();
        createRemoteSessionRepo();
        createSessionFactory();
        createLocalSessionRepo();
        return new Tenant(tenant,
                          TenantRepository.getTenantPath(tenant),
                          sessionFactory,
                          localSessionRepo,
                          remoteSessionRepo,
                          requestHandler,
                          reloadHandler,
                          applicationRepo,
                          componentRegistry.getCurator());
    }

	private void createLocalSessionRepo() {
        if (localSessionRepo == null) {
            localSessionRepo = new LocalSessionRepo(tenant, componentRegistry, localSessionLoader);
        }
    }

    private void createSessionFactory() {
        if (sessionFactory == null || localSessionLoader == null) {
            SessionFactoryImpl impl = new SessionFactoryImpl(componentRegistry, applicationRepo, hostValidator, tenant);
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
            applicationRepo = reloadHandler.applications();
        }
    }

    private void createTenantRequestHandler() {
        if (requestHandler == null || reloadHandler == null) {
            TenantRequestHandler impl = new TenantRequestHandler(componentRegistry.getMetrics(),
                                                                 tenant,
                                                                 Collections.singletonList(componentRegistry.getReloadListener()),
                                                                 ConfigResponseFactory.create(componentRegistry.getConfigserverConfig()),
                                                                 componentRegistry);
            this.hostValidator = impl;
            if (requestHandler == null) {
                requestHandler = impl;
            }
            reloadHandler = impl;
        }
    }

    private void createRemoteSessionFactory() {
        if (remoteSessionFactory == null) {
            remoteSessionFactory = new RemoteSessionFactory(componentRegistry, tenant);
        }
    }

    private void createRemoteSessionRepo() {
        remoteSessionRepo = new RemoteSessionRepo(componentRegistry,
                                                  remoteSessionFactory,
                                                  reloadHandler,
                                                  tenant,
                                                  applicationRepo);

    }

    public TenantName getTenantName() { return tenant; }
}
