// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;

import java.io.File;
import java.io.FilenameFilter;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * File-based session repository for LocalSessions. Contains state for the local instance of the configserver.
 *
 * @author lulf
 */
public class LocalSessionRepo extends SessionRepo<LocalSession> {

    private static final Logger log = Logger.getLogger(LocalSessionRepo.class.getName());
    private static final FilenameFilter sessionApplicationsFilter = (dir, name) -> name.matches("\\d+");
    private static final Duration delay = Duration.ofMinutes(5);

    private final ScheduledExecutorService purgeOldSessionsExecutor = new ScheduledThreadPoolExecutor(1);
    private final long sessionLifetime; // in seconds
    private final Clock clock;

    public LocalSessionRepo(TenantFileSystemDirs tenantFileSystemDirs, LocalSessionLoader loader,
                            Clock clock, long sessionLifeTime) {
        this(clock, sessionLifeTime);
        loadSessions(tenantFileSystemDirs.sessionsPath(), loader);
        purgeOldSessionsExecutor.scheduleWithFixedDelay(this::purgeOldSessions, delay.getSeconds(), delay.getSeconds(), TimeUnit.SECONDS);
    }

    // Constructor public only for testing
    public LocalSessionRepo(Clock clock) {
        this(clock, TimeUnit.DAYS.toMillis(1));
    }

    // Constructor public only for testing
    private LocalSessionRepo(Clock clock, long sessionLifetime) {
        this.sessionLifetime = sessionLifetime;
        this.clock = clock;
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

    private void deleteSession(LocalSession candidate) {
        removeSessionOrThrow(candidate.getSessionId());
        candidate.delete();
    }

    public void deleteAllSessions() {
        List<LocalSession> sessions = new ArrayList<>(listSessions());
        for (LocalSession session : sessions) {
            deleteSession(session);
        }
    }
}
