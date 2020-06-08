// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.session.RemoteSessionRepo;
import com.yahoo.vespa.config.server.session.SessionFactory;
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
    private final RemoteSessionRepo remoteSessionRepo;
    private final Path path;
    private final SessionFactory sessionFactory;
    private final SessionRepository sessionRepository;
    private final TenantApplications applicationRepo;
    private final RequestHandler requestHandler;
    private final ReloadHandler reloadHandler;
    private final Curator curator;

    Tenant(TenantName name,
           SessionFactory sessionFactory,
           SessionRepository sessionRepository,
           RemoteSessionRepo remoteSessionRepo,
           RequestHandler requestHandler,
           ReloadHandler reloadHandler,
           TenantApplications applicationRepo,
           Curator curator) {
        this.name = name;
        this.path = TenantRepository.getTenantPath(name);
        this.requestHandler = requestHandler;
        this.reloadHandler = reloadHandler;
        this.remoteSessionRepo = remoteSessionRepo;
        this.sessionFactory = sessionFactory;
        this.sessionRepository = sessionRepository;
        this.applicationRepo = applicationRepo;
        this.curator = curator;
    }

    /**
     * The reload handler for this
     *
     * @return handler
     */
    public ReloadHandler getReloadHandler() {
        return reloadHandler;
    }

    /**
     * The request handler for this
     *
     * @return handler
     */
    public RequestHandler getRequestHandler() {
        return requestHandler;
    }

    /**
     * The RemoteSessionRepo for this
     *
     * @return repo
     */
    public RemoteSessionRepo getRemoteSessionRepo() {
        return remoteSessionRepo;
    }

    public TenantName getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }

    @Override
    public String toString() {
        return getName().value();
    }

    public TenantApplications getApplicationRepo() {
        return applicationRepo;
    }

    public Curator getCurator() {
        return curator;
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
     * Called by watchers as a reaction to {@link #delete()}.
     */
    void close() {
        remoteSessionRepo.close();              // Closes watchers and clears memory.
        applicationRepo.close();                // Closes watchers.
        sessionRepository.close();               // Closes watchers, clears memory, and deletes local files and ZK session state.
    }

    /** Deletes the tenant tree from ZooKeeper (application and session status for the tenant) and triggers {@link #close()}. */
    void delete() {
        curator.delete(path);                   // Deletes tenant ZK tree: applications and sessions.
    }

}
