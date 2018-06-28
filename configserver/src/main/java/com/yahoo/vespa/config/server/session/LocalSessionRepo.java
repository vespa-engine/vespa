// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.io.FilenameFilter;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
    private static final Duration delay = Duration.ofMinutes(5);

    // One executor for all instances of this class
    private static final ScheduledExecutorService purgeOldSessionsExecutor =
            new ScheduledThreadPoolExecutor(1, ThreadFactoryFactory.getDaemonThreadFactory("purge-old-sessions"));
    private final Map<Long, LocalSessionStateWatcher> sessionStateWatchers = new HashMap<>();
    private final long sessionLifetime; // in seconds
    private final Clock clock;
    private final Curator curator;

    public LocalSessionRepo(TenantFileSystemDirs tenantFileSystemDirs, LocalSessionLoader loader,
                            Clock clock, long sessionLifeTime, Curator curator) {
        this(clock, curator, sessionLifeTime);
        loadSessions(tenantFileSystemDirs.sessionsPath(), loader);
        purgeOldSessionsExecutor.scheduleWithFixedDelay(this::purgeOldSessions, delay.getSeconds(), delay.getSeconds(), TimeUnit.SECONDS);
    }

    // Constructor public only for testing
    public LocalSessionRepo(Clock clock, Curator curator) {
        this(clock, curator, TimeUnit.DAYS.toMillis(1));
    }

    // Constructor public only for testing
    private LocalSessionRepo(Clock clock, Curator curator, long sessionLifetime) {
        this.clock = clock;
        this.curator = curator;
        this.sessionLifetime = sessionLifetime;
    }

    @Override
    public synchronized void addSession(LocalSession session) {
        super.addSession(session);
        Path sessionsPath = TenantRepository.getSessionsPath(session.getTenantName());
        long sessionId = session.getSessionId();
        Curator.FileCache fileCache = curator.createFileCache(sessionsPath.append(String.valueOf(sessionId)).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute(), false);
        sessionStateWatchers.put(sessionId, new LocalSessionStateWatcher(fileCache, session, this));
    }

    private void loadSessions(File applicationsDir, LocalSessionLoader loader) {
        File[] applications = applicationsDir.listFiles(sessionApplicationsFilter);
        if (applications == null) {
            return;
        }
        for (File application : applications) {
            try {
                addSession(loader.loadSession(Long.parseLong(application.getName())));
            } catch (IllegalArgumentException e) {
                log.log(LogLevel.WARNING, "Could not load application '" +
                        application.getAbsolutePath() + "':" + e.getMessage() + ", skipping it.");
            }
        }
    }

    // public for testing
    public void purgeOldSessions() {
        log.log(LogLevel.DEBUG, "Purging old sessions");
        try {
            List<LocalSession> sessions = new ArrayList<>(listSessions());
            for (LocalSession candidate : sessions) {
                if (hasExpired(candidate) && !isActiveSession(candidate)) {
                    deleteSession(candidate);
                }
            }
            // Make sure to catch here, to avoid executor just dying in case of issues ...
        } catch (Throwable e) {
            log.log(LogLevel.WARNING, "Error when purging old sessions ", e);
        }
        log.log(LogLevel.DEBUG, "Done purging old sessions");
    }

    private boolean hasExpired(LocalSession candidate) {
        return (candidate.getCreateTime() + sessionLifetime) <= TimeUnit.MILLISECONDS.toSeconds(clock.millis());
    }

    private boolean isActiveSession(LocalSession candidate) {
        return candidate.getStatus() == Session.Status.ACTIVATE;
    }

    void deleteSession(LocalSession session) {
        long sessionId = session.getSessionId();
        log.log(LogLevel.DEBUG, "Deleting local session " + sessionId);
        LocalSessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null)  watcher.close();
        removeSession(sessionId);
        NestedTransaction transaction = new NestedTransaction();
        session.delete(transaction);
        transaction.commit();
    }

    public void deleteAllSessions() {
        List<LocalSession> sessions = new ArrayList<>(listSessions());
        for (LocalSession session : sessions) {
            deleteSession(session);
        }
    }
}
