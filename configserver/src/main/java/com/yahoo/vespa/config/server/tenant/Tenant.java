// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.curator.Curator;
import org.apache.zookeeper.data.Stat;

import java.time.Instant;
import java.util.Optional;

/**
 * Contains all tenant-level components for a single tenant, dealing with editing sessions and
 * applications for a single tenant.
 *
 * @author vegardh
 * @author Ulf Lilleengen
 */
public class Tenant implements TenantHandlerProvider {

    static final String SESSIONS = "sessions";
    static final String APPLICATIONS = "applications";

    private final TenantName name;
    private final Path path;
    private final SessionRepository sessionRepository;
    private final TenantApplications applicationRepo;
    private final RequestHandler requestHandler;
    private final Curator curator;

    Tenant(TenantName name,
           SessionRepository sessionRepository,
           RequestHandler requestHandler,
           TenantApplications applicationRepo,
           Curator curator) {
        this.name = name;
        this.path = TenantRepository.getTenantPath(name);
        this.requestHandler = requestHandler;
        this.sessionRepository = sessionRepository;
        this.applicationRepo = applicationRepo;
        this.curator = curator;
    }

    /**
     * The request handler for this
     *
     * @return handler
     */
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
        Optional<Stat> stat = curator.getStat(path);
        if (stat.isPresent())
            return Instant.ofEpochMilli(stat.get().getCtime());
        else
            return Instant.now();
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
        applicationRepo.close();                // Closes watchers.
        sessionRepository.close();              // Closes watchers, clears memory, and deletes local files and ZK session state.
    }

}
