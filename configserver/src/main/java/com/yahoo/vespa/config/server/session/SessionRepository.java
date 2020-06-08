// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

import java.io.File;
import java.io.FilenameFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Session repository for config server. Stores session state in zookeeper and file system. There are two
 * different session types (RemoteSession and LocalSession).
 *
 * @author Ulf Lilleengen
 */
public class SessionRepository {

    private static final Logger log = Logger.getLogger(SessionRepository.class.getName());
    private static final FilenameFilter sessionApplicationsFilter = (dir, name) -> name.matches("\\d+");

    private final SessionCache<LocalSession> localSessionCache = new SessionCache<>();
    private final SessionCache<RemoteSession> remoteSessionCache = new SessionCache<>();
    private final Map<Long, LocalSessionStateWatcher> localSessionStateWatchers = new HashMap<>();
    private final Map<Long, RemoteSessionStateWatcher> remoteSessionStateWatchers = new HashMap<>();
    private final Duration sessionLifetime;
    private final Clock clock;
    private final Curator curator;
    private final Executor zkWatcherExecutor;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final ReloadHandler reloadHandler;
    private final MetricUpdater metrics;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;
    private final Path sessionsPath;
    private final SessionFactory sessionFactory;
    private final TenantName tenantName;


    public SessionRepository(TenantName tenantName, GlobalComponentRegistry componentRegistry,
                             SessionFactory sessionFactory, TenantApplications applicationRepo,
                             ReloadHandler reloadHandler) {
        this.tenantName = tenantName;
        this.clock = componentRegistry.getClock();
        this.curator = componentRegistry.getCurator();
        this.sessionLifetime = Duration.ofSeconds(componentRegistry.getConfigserverConfig().sessionLifetime());
        this.zkWatcherExecutor = command -> componentRegistry.getZkWatcherExecutor().execute(tenantName, command);
        this.tenantFileSystemDirs = new TenantFileSystemDirs(componentRegistry.getConfigServerDB(), tenantName);
        this.sessionFactory = sessionFactory;
        this.applicationRepo = applicationRepo;
        loadLocalSessions(sessionFactory);

        this.reloadHandler = reloadHandler;
        this.sessionsPath = TenantRepository.getSessionsPath(tenantName);
        this.metrics = componentRegistry.getMetrics().getOrCreateMetricUpdater(Metrics.createDimensions(tenantName));
        initializeRemoteSessions();
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, componentRegistry.getZkCacheExecutor());
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
    }

    // ---------------- Local sessions ----------------------------------------------------------------

    public synchronized void addSession(LocalSession session) {
        localSessionCache.addSession(session);
        Path sessionsPath = TenantRepository.getSessionsPath(session.getTenantName());
        long sessionId = session.getSessionId();
        Curator.FileCache fileCache = curator.createFileCache(sessionsPath.append(String.valueOf(sessionId)).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute(), false);
        localSessionStateWatchers.put(sessionId, new LocalSessionStateWatcher(fileCache, session, this, zkWatcherExecutor));
    }

    public LocalSession getSession(long sessionId) {
        return localSessionCache.getSession(sessionId);
    }

    public List<LocalSession> getSessions() {
        return localSessionCache.getSessions();
    }

    private void loadLocalSessions(SessionFactory sessionFactory) {
        File[] sessions = tenantFileSystemDirs.sessionsPath().listFiles(sessionApplicationsFilter);
        if (sessions == null) {
            return;
        }
        for (File session : sessions) {
            try {
                addSession(sessionFactory.createSessionFromId(Long.parseLong(session.getName())));
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Could not load session '" +
                                       session.getAbsolutePath() + "':" + e.getMessage() + ", skipping it.");
            }
        }
    }

    public void deleteExpiredSessions(Map<ApplicationId, Long> activeSessions) {
        log.log(Level.FINE, "Purging old sessions");
        try {
            for (LocalSession candidate : localSessionCache.getSessions()) {
                Instant createTime = candidate.getCreateTime();
                log.log(Level.FINE, "Candidate session for deletion: " + candidate.getSessionId() + ", created: " + createTime);

                // Sessions with state other than ACTIVATED
                if (hasExpired(candidate) && !isActiveSession(candidate)) {
                    deleteSession(candidate);
                } else if (createTime.plus(Duration.ofDays(1)).isBefore(clock.instant())) {
                    //  Sessions with state ACTIVATE, but which are not actually active
                    ApplicationId applicationId = candidate.getApplicationId();
                    Long activeSession = activeSessions.get(applicationId);
                    if (activeSession == null || activeSession != candidate.getSessionId()) {
                        deleteSession(candidate);
                        log.log(Level.INFO, "Deleted inactive session " + candidate.getSessionId() + " created " +
                                            createTime + " for '" + applicationId + "'");
                    }
                }
            }
            // Make sure to catch here, to avoid executor just dying in case of issues ...
        } catch (Throwable e) {
            log.log(Level.WARNING, "Error when purging old sessions ", e);
        }
        log.log(Level.FINE, "Done purging old sessions");
    }

    private boolean hasExpired(LocalSession candidate) {
        return (candidate.getCreateTime().plus(sessionLifetime).isBefore(clock.instant()));
    }

    private boolean isActiveSession(LocalSession candidate) {
        return candidate.getStatus() == Session.Status.ACTIVATE;
    }

    public void deleteSession(LocalSession session) {
        long sessionId = session.getSessionId();
        log.log(Level.FINE, "Deleting local session " + sessionId);
        LocalSessionStateWatcher watcher = localSessionStateWatchers.remove(sessionId);
        if (watcher != null) watcher.close();
        localSessionCache.removeSession(sessionId);
        NestedTransaction transaction = new NestedTransaction();
        session.delete(transaction);
        transaction.commit();
    }

    public void close() {
        deleteAllSessions();
        tenantFileSystemDirs.delete();
        try {
            if (directoryCache != null) {
                directoryCache.close();
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception when closing path cache", e);
        } finally {
            checkForRemovedSessions(new ArrayList<>());
        }
    }

    private void deleteAllSessions() {
        List<LocalSession> sessions = new ArrayList<>(localSessionCache.getSessions());
        for (LocalSession session : sessions) {
            deleteSession(session);
        }
    }

    // ---------------- Remote sessions ----------------------------------------------------------------

    public RemoteSession getRemoteSession(long sessionId) {
        return remoteSessionCache.getSession(sessionId);
    }

    public List<Long> getRemoteSessions() {
        return getRemoteSessionList(curator.getChildren(sessionsPath));
    }

    public void addRemoteSession(RemoteSession session) {
        remoteSessionCache.addSession(session);
    }

    public int deleteExpiredRemoteSessions(Clock clock, Duration expiryTime) {
        int deleted = 0;
        for (long sessionId : getRemoteSessions()) {
            RemoteSession session = remoteSessionCache.getSession(sessionId);
            if (session == null) continue; // Internal sessions not in synch with zk, continue
            if (session.getStatus() == Session.Status.ACTIVATE) continue;
            if (remoteSessionHasExpired(session.getCreateTime(), expiryTime, clock)) {
                log.log(Level.INFO, "Remote session " + sessionId + " for " + tenantName + " has expired, deleting it");
                session.delete();
                deleted++;
            }
        }
        return deleted;
    }

    private boolean remoteSessionHasExpired(Instant created, Duration expiryTime, Clock clock) {
        return (created.plus(expiryTime).isBefore(clock.instant()));
    }

    private List<Long> getRemoteSessionListFromDirectoryCache(List<ChildData> children) {
        return getRemoteSessionList(children.stream()
                                            .map(child -> Path.fromString(child.getPath()).getName())
                                            .collect(Collectors.toList()));
    }

    private List<Long> getRemoteSessionList(List<String> children) {
        return children.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    private void initializeRemoteSessions() throws NumberFormatException {
        getRemoteSessions().forEach(this::sessionAdded);
    }

    private synchronized void remoteSessionsChanged() throws NumberFormatException {
        List<Long> sessions = getRemoteSessionListFromDirectoryCache(directoryCache.getCurrentData());
        checkForRemovedSessions(sessions);
        checkForAddedSessions(sessions);
    }

    private void checkForRemovedSessions(List<Long> sessions) {
        for (RemoteSession session : remoteSessionCache.getSessions())
            if (!sessions.contains(session.getSessionId()))
                sessionRemoved(session.getSessionId());
    }

    private void checkForAddedSessions(List<Long> sessions) {
        for (Long sessionId : sessions)
            if (remoteSessionCache.getSession(sessionId) == null)
                sessionAdded(sessionId);
    }

    /**
     * A session for which we don't have a watcher, i.e. hitherto unknown to us.
     *
     * @param sessionId session id for the new session
     */
    private void sessionAdded(long sessionId) {
        log.log(Level.FINE, () -> "Adding session to RemoteSessionRepo: " + sessionId);
        RemoteSession session = sessionFactory.createRemoteSession(sessionId);
        Path sessionPath = sessionsPath.append(String.valueOf(sessionId));
        Curator.FileCache fileCache = curator.createFileCache(sessionPath.append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute(), false);
        fileCache.addListener(this::nodeChanged);
        loadSessionIfActive(session);
        addRemoteSession(session);
        metrics.incAddedSessions();
        remoteSessionStateWatchers.put(sessionId, new RemoteSessionStateWatcher(fileCache, reloadHandler, session, metrics, zkWatcherExecutor));
    }

    private void sessionRemoved(long sessionId) {
        RemoteSessionStateWatcher watcher = remoteSessionStateWatchers.remove(sessionId);
        if (watcher != null) watcher.close();
        remoteSessionCache.removeSession(sessionId);
        metrics.incRemovedSessions();
    }

    private void loadSessionIfActive(RemoteSession session) {
        for (ApplicationId applicationId : applicationRepo.activeApplications()) {
            if (applicationRepo.requireActiveSessionOf(applicationId) == session.getSessionId()) {
                log.log(Level.FINE, () -> "Found active application for session " + session.getSessionId() + " , loading it");
                reloadHandler.reloadConfig(session.ensureApplicationLoaded());
                log.log(Level.INFO, session.logPre() + "Application activated successfully: " + applicationId + " (generation " + session.getSessionId() + ")");
                return;
            }
        }
    }

    private void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            Multiset<Session.Status> sessionMetrics = HashMultiset.create();
            for (RemoteSession session : remoteSessionCache.getSessions()) {
                sessionMetrics.add(session.getStatus());
            }
            metrics.setNewSessions(sessionMetrics.count(Session.Status.NEW));
            metrics.setPreparedSessions(sessionMetrics.count(Session.Status.PREPARE));
            metrics.setActivatedSessions(sessionMetrics.count(Session.Status.ACTIVATE));
            metrics.setDeactivatedSessions(sessionMetrics.count(Session.Status.DEACTIVATE));
        });
    }

    @SuppressWarnings("unused")
    private void childEvent(CuratorFramework ignored, PathChildrenCacheEvent event) {
        zkWatcherExecutor.execute(() -> {
            log.log(Level.FINE, () -> "Got child event: " + event);
            switch (event.getType()) {
                case CHILD_ADDED:
                    remoteSessionsChanged();
                    synchronizeOnNew(getRemoteSessionListFromDirectoryCache(Collections.singletonList(event.getData())));
                    break;
                case CHILD_REMOVED:
                    remoteSessionsChanged();
                    break;
                case CONNECTION_RECONNECTED:
                    remoteSessionsChanged();
                    break;
            }
        });
    }

    private void synchronizeOnNew(List<Long> sessionList) {
        for (long sessionId : sessionList) {
            RemoteSession session = remoteSessionCache.getSession(sessionId);
            if (session == null) continue; // session might have been deleted after getting session list
            log.log(Level.FINE, () -> session.logPre() + "Confirming upload for session " + sessionId);
            session.confirmUpload();
        }
    }

    @Override
    public String toString() {
        return getSessions().toString();
    }

}
