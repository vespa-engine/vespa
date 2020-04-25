// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import java.util.logging.Level;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

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
 * Will watch/prepare sessions (applications) based on watched nodes in ZooKeeper. The zookeeper state watched in
 * this class is shared between all config servers, so it should not modify any global state, because the operation
 * will be performed on all servers. The repo can be regarded as read only from the POV of the configserver.
 *
 * @author Vegard Havdal
 * @author Ulf Lilleengen
 */
public class RemoteSessionRepo extends SessionRepo<RemoteSession> {

    private static final Logger log = Logger.getLogger(RemoteSessionRepo.class.getName());

    private final GlobalComponentRegistry componentRegistry;
    private final Curator curator;
    private final Path sessionsPath;
    private final RemoteSessionFactory remoteSessionFactory;
    private final Map<Long, RemoteSessionStateWatcher> sessionStateWatchers = new HashMap<>();
    private final ReloadHandler reloadHandler;
    private final TenantName tenantName;
    private final MetricUpdater metrics;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;
    private final Executor zkWatcherExecutor;

    public RemoteSessionRepo(GlobalComponentRegistry componentRegistry,
                             RemoteSessionFactory remoteSessionFactory,
                             ReloadHandler reloadHandler,
                             TenantName tenantName,
                             TenantApplications applicationRepo) {
        this.componentRegistry = componentRegistry;
        this.curator = componentRegistry.getCurator();
        this.sessionsPath = TenantRepository.getSessionsPath(tenantName);
        this.applicationRepo = applicationRepo;
        this.remoteSessionFactory = remoteSessionFactory;
        this.reloadHandler = reloadHandler;
        this.tenantName = tenantName;
        this.metrics = componentRegistry.getMetrics().getOrCreateMetricUpdater(Metrics.createDimensions(tenantName));
        StripedExecutor<TenantName> zkWatcherExecutor = componentRegistry.getZkWatcherExecutor();
        this.zkWatcherExecutor = command -> zkWatcherExecutor.execute(tenantName, command);
        initializeSessions();
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, componentRegistry.getZkCacheExecutor());
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
    }

    public List<Long> getSessions() {
        return getSessionList(curator.getChildren(sessionsPath));
    }

    public int deleteExpiredSessions(Duration expiryTime) {
        int deleted = 0;
        for (long sessionId : getSessions()) {
            RemoteSession session = getSession(sessionId);
            if (session == null) continue; // Internal sessions not in synch with zk, continue
            Instant created = Instant.ofEpochSecond(session.getCreateTime());
            if (sessionHasExpired(created, expiryTime)) {
                log.log(Level.INFO, "Remote session " + sessionId + " for " + tenantName + " has expired, deleting it");
                session.delete();
                deleted++;
            }
        }
        return deleted;
    }

    private boolean sessionHasExpired(Instant created, Duration expiryTime) {
        return (created.plus(expiryTime).isBefore(Instant.now()));
    }

    private List<Long> getSessionListFromDirectoryCache(List<ChildData> children) {
        return getSessionList(children.stream()
                                      .map(child -> Path.fromString(child.getPath()).getName())
                                      .collect(Collectors.toList()));
    }

    private List<Long> getSessionList(List<String> children) {
        return children.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    private void initializeSessions() throws NumberFormatException {
        getSessions().forEach(this::sessionAdded);
    }

    private synchronized void sessionsChanged() throws NumberFormatException {
        List<Long> sessions = getSessionListFromDirectoryCache(directoryCache.getCurrentData());
        checkForRemovedSessions(sessions);
        checkForAddedSessions(sessions);
    }

    private void checkForRemovedSessions(List<Long> sessions) {
        for (RemoteSession session : listSessions())
            if ( ! sessions.contains(session.getSessionId()))
                sessionRemoved(session.getSessionId());
    }
    
    private void checkForAddedSessions(List<Long> sessions) {
        for (Long sessionId : sessions)
            if (getSession(sessionId) == null)
                sessionAdded(sessionId);
    }

    /**
     * A session for which we don't have a watcher, i.e. hitherto unknown to us.
     *
     * @param sessionId session id for the new session
     */
    private void sessionAdded(long sessionId) {
        log.log(Level.FINE, () -> "Adding session to RemoteSessionRepo: " + sessionId);
        try {
            RemoteSession session = remoteSessionFactory.createSession(sessionId);
            Path sessionPath = sessionsPath.append(String.valueOf(sessionId));
            Curator.FileCache fileCache = curator.createFileCache(sessionPath.append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute(), false);
            fileCache.addListener(this::nodeChanged);
            loadSessionIfActive(session);
            sessionStateWatchers.put(sessionId, new RemoteSessionStateWatcher(fileCache, reloadHandler, session, metrics, zkWatcherExecutor));
            addSession(session);
            metrics.incAddedSessions();
        } catch (Exception e) {
            if (componentRegistry.getConfigserverConfig().throwIfActiveSessionCannotBeLoaded()) throw e;
            log.log(Level.WARNING, "Failed loading session " + sessionId + ": No config for this session can be served", e);
        }
    }

    private void sessionRemoved(long sessionId) {
        RemoteSessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null)  watcher.close();
        removeSession(sessionId);
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

    public synchronized void close() {
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

    private void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            Multiset<Session.Status> sessionMetrics = HashMultiset.create();
            for (RemoteSession session : listSessions()) {
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
                    sessionsChanged();
                    synchronizeOnNew(getSessionListFromDirectoryCache(Collections.singletonList(event.getData())));
                    break;
                case CHILD_REMOVED:
                    sessionsChanged();
                    break;
                case CONNECTION_RECONNECTED:
                    sessionsChanged();
                    break;
            }
        });
    }

    private void synchronizeOnNew(List<Long> sessionList) {
        for (long sessionId : sessionList) {
            RemoteSession session = getSession(sessionId);
            if (session == null) continue; // session might have been deleted after getting session list
            log.log(Level.FINE, () -> session.logPre() + "Confirming upload for session " + sessionId);
            session.confirmUpload();
        }
    }

}
