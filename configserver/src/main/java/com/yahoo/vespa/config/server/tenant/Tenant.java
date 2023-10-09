// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.session.SessionRepository;

import java.time.Instant;

/**
 * Tenant, mostly a wrapper for sessions and applications belonging to a tenant
 *
 * @author vegardh
 * @author Ulf Lilleengen
 */
public class Tenant {

    static final String SESSIONS = "sessions";
    static final String APPLICATIONS = "applications";

    private final TenantName name;
    private final Path path;
    private final SessionRepository sessionRepository;
    private final TenantApplications applicationRepo;
    private final RequestHandler requestHandler;
    private final Instant created;

    Tenant(TenantName name, SessionRepository sessionRepository, TenantApplications applicationRepo, Instant created) {
        this(name, sessionRepository, applicationRepo, applicationRepo, created);
    }

    // Protected due to being subclassed in a system test
    protected Tenant(TenantName name,
                     SessionRepository sessionRepository,
                     RequestHandler requestHandler,
                     TenantApplications applicationRepo,
                     Instant created) {
        this.name = name;
        this.path = TenantRepository.getTenantPath(name);
        this.requestHandler = requestHandler;
        this.sessionRepository = sessionRepository;
        this.applicationRepo = applicationRepo;
        this.created = created;
    }

    public RequestHandler getRequestHandler() {
        return requestHandler;
    }

    public TenantName getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public SessionRepository getSessionRepository() { return sessionRepository; }

    @Override
    public String toString() {
        return getName().value();
    }

    public TenantApplications getApplicationRepo() {
        return applicationRepo;
    }

    public Instant getCreatedTime() {
        return created;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tenant)) {
            return false;
        }
        Tenant that = (Tenant) other;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Closes any watchers, thread pools that may react to changes in tenant state,
     * and removes any session data in filesystem and zookeeper.
     * Called by watchers as a reaction to deleting a tenant.
     */
    void close() {
        applicationRepo.close();   // Closes watchers.
        sessionRepository.close(); // Closes watchers, clears memory, and deletes local files and ZK session state.
    }

}
