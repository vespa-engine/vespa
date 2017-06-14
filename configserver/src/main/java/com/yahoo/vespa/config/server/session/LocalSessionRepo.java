// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.log.LogLevel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;

import java.io.File;
import java.io.FilenameFilter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * File-based session repository for LocalSessions. Contains state for the local instance of the configserver.
 *
 * @author lulf
 * @since 5.1
 */
public class LocalSessionRepo extends SessionRepo<LocalSession> {

    private static final Logger log = Logger.getLogger(LocalSessionRepo.class.getName());

    private final static FilenameFilter sessionApplicationsFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.matches("\\d+");
        }
    };

    private final long sessionLifetime; // in seconds
    private final TenantApplications applicationRepo;
    private final Clock clock;

    public LocalSessionRepo(TenantFileSystemDirs tenantFileSystemDirs, LocalSessionLoader loader, TenantApplications applicationRepo, Clock clock, long sessionLifeTime) {
        this(applicationRepo, clock, sessionLifeTime);
        loadSessions(tenantFileSystemDirs.path(), loader);
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
                log.log(LogLevel.WARNING, "Could not load application '" + application.getAbsolutePath() + "':" + e.getMessage() + ", skipping it.");
            }
        }
    }

    /**
     * Gets the active Session for the given application id.
     *
     * @return the active session, or null if there is no active session for the given application id.
     */
    public LocalSession getActiveSession(ApplicationId applicationId) {
        List<ApplicationId> applicationIds = applicationRepo.listApplications();
        if (applicationIds.contains(applicationId)) {
            return getSession(applicationRepo.getSessionIdForApplication(applicationId));
        }
        return null;
    }

    // Constructor only for testing
    public LocalSessionRepo(TenantApplications applicationRepo, Clock clock, long sessionLifetime) {
        this.applicationRepo = applicationRepo;
        this.sessionLifetime = sessionLifetime;
        this.clock = clock;
    }

    public LocalSessionRepo(TenantApplications applicationRepo) {
        this(applicationRepo, Clock.systemUTC(), TimeUnit.DAYS.toMillis(1));
    }

    @Override
    public synchronized void addSession(LocalSession session) {
        purgeOldSessions();
        super.addSession(session);
    }

    private void purgeOldSessions() {
        final List<ApplicationId> applicationIds = applicationRepo.listApplications();
        List<LocalSession> sessions = new ArrayList<>(listSessions());
        for (LocalSession candidate : sessions) {
            if (hasExpired(candidate) && !isActiveSession(candidate, applicationIds)) {
                deleteSession(candidate);
            }
        }
    }

    private boolean hasExpired(LocalSession candidate) {
        return (candidate.getCreateTime() + sessionLifetime) <= TimeUnit.MILLISECONDS.toSeconds(clock.millis());
    }

    private boolean isActiveSession(LocalSession candidate, List<ApplicationId> activeIds) {
        if (candidate.getStatus() == Session.Status.ACTIVATE && activeIds.contains(candidate.getApplicationId())) {
            long sessionId = applicationRepo.getSessionIdForApplication(candidate.getApplicationId());
            return (candidate.getSessionId() == sessionId);
        } else {
            return false;
        }
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
