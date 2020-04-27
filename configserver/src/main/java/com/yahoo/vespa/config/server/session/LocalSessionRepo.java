// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.TenantName;
import java.util.logging.Level;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.io.FilenameFilter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * File-based session repository for LocalSessions. Contains state for the local instance of the configserver.
 *
 * @author Ulf Lilleengen
 */
public class LocalSessionRepo extends SessionRepo<LocalSession> {

    private static final Logger log = Logger.getLogger(LocalSessionRepo.class.getName());
    private static final FilenameFilter sessionApplicationsFilter = (dir, name) -> name.matches("\\d+");

    private final Map<Long, LocalSessionStateWatcher> sessionStateWatchers = new HashMap<>();
    private final long sessionLifetime; // in seconds
    private final Clock clock;
    private final Curator curator;
    private final Executor zkWatcherExecutor;
    private final TenantFileSystemDirs tenantFileSystemDirs;

    public LocalSessionRepo(TenantName tenantName, GlobalComponentRegistry componentRegistry, LocalSessionLoader loader) {
        this(tenantName, componentRegistry);
        loadSessions(loader);
    }

    // Constructor public only for testing
    public LocalSessionRepo(TenantName tenantName, GlobalComponentRegistry componentRegistry) {
        this.clock = componentRegistry.getClock();
        this.curator = componentRegistry.getCurator();
        this.sessionLifetime = componentRegistry.getConfigserverConfig().sessionLifetime();
        this.zkWatcherExecutor = command -> componentRegistry.getZkWatcherExecutor().execute(tenantName, command);
        this.tenantFileSystemDirs = new TenantFileSystemDirs(componentRegistry.getConfigServerDB(), tenantName);
    }

    @Override
    public synchronized void addSession(LocalSession session) {
        super.addSession(session);
        Path sessionsPath = TenantRepository.getSessionsPath(session.getTenantName());
        long sessionId = session.getSessionId();
        Curator.FileCache fileCache = curator.createFileCache(sessionsPath.append(String.valueOf(sessionId)).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute(), false);
        sessionStateWatchers.put(sessionId, new LocalSessionStateWatcher(fileCache, session, this, zkWatcherExecutor));
    }

    private void loadSessions(LocalSessionLoader loader) {
        File[] sessions = tenantFileSystemDirs.sessionsPath().listFiles(sessionApplicationsFilter);
        if (sessions == null) {
            return;
        }
        for (File session : sessions) {
            try {
                addSession(loader.loadSession(Long.parseLong(session.getName())));
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Could not load session '" +
                        session.getAbsolutePath() + "':" + e.getMessage() + ", skipping it.");
            }
        }
    }

    public void purgeOldSessions() {
        log.log(Level.FINE, "Purging old sessions");
        try {
            List<LocalSession> sessions = new ArrayList<>(listSessions());
            for (LocalSession candidate : sessions) {
                if (hasExpired(candidate) && !isActiveSession(candidate)) {
                    deleteSession(candidate);
                }
            }
            // Make sure to catch here, to avoid executor just dying in case of issues ...
        } catch (Throwable e) {
            log.log(Level.WARNING, "Error when purging old sessions ", e);
        }
        log.log(Level.FINE, "Done purging old sessions");
    }

    private boolean hasExpired(LocalSession candidate) {
        return (candidate.getCreateTime() + sessionLifetime) <= TimeUnit.MILLISECONDS.toSeconds(clock.millis());
    }

    private boolean isActiveSession(LocalSession candidate) {
        return candidate.getStatus() == Session.Status.ACTIVATE;
    }

    void deleteSession(LocalSession session) {
        long sessionId = session.getSessionId();
        log.log(Level.FINE, "Deleting local session " + sessionId);
        LocalSessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null)  watcher.close();
        removeSession(sessionId);
        NestedTransaction transaction = new NestedTransaction();
        session.delete(transaction);
        transaction.commit();
    }

    public void close() {
        deleteAllSessions();
        tenantFileSystemDirs.delete();
    }

    private void deleteAllSessions() {
        List<LocalSession> sessions = new ArrayList<>(listSessions());
        for (LocalSession session : sessions) {
            deleteSession(session);
        }
    }
}
