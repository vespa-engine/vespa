// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.yahoo.config.provision.TenantName;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.*;

/**
 * Will watch/prepare sessions (applications) based on watched nodes in ZooKeeper, set for example
 * by the prepare HTTP handler on another configserver. The zookeeper state watched in this class is shared
 * between all config servers, so it should not modify any global state, because the operation will be performed
 * on all servers. The repo can be regarded as read only from the POV of the configserver.
 *
 * @author Vegard Havdal
 * @author Ulf Lilleengen
 */
public class RemoteSessionRepo extends SessionRepo<RemoteSession> implements NodeCacheListener, PathChildrenCacheListener {

    private static final Logger log = Logger.getLogger(RemoteSessionRepo.class.getName());
    private final Curator curator;
    private final Path sessionsPath;
    private final RemoteSessionFactory remoteSessionFactory;
    private final Map<Long, SessionStateWatcher> sessionStateWatchers = new HashMap<>();
    private final ReloadHandler reloadHandler;
    private final MetricUpdater metrics;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;

    /**
     * @param curator              a {@link Curator} instance.
     * @param remoteSessionFactory a {@link com.yahoo.vespa.config.server.session.RemoteSessionFactory}
     * @param reloadHandler        a {@link com.yahoo.vespa.config.server.ReloadHandler}
     * @param tenant               a {@link TenantName} instance.
     * @param applicationRepo      a {@link TenantApplications} instance.
     * @param executorService      an {@link ExecutorService} to run callbacks from ZooKeeper.
     */
    public RemoteSessionRepo(Curator curator,
                             RemoteSessionFactory remoteSessionFactory,
                             ReloadHandler reloadHandler,
                             TenantName tenant,
                             TenantApplications applicationRepo,
                             MetricUpdater metricUpdater,
                             ExecutorService executorService) {
        this.curator = curator;
        this.sessionsPath = TenantRepository.getSessionsPath(tenant);
        this.applicationRepo = applicationRepo;
        this.remoteSessionFactory = remoteSessionFactory;
        this.reloadHandler = reloadHandler;
        this.metrics = metricUpdater;
        initializeSessions();
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, executorService);
        this.directoryCache.addListener(this);
        this.directoryCache.start();
    }

    // For testing only
    public RemoteSessionRepo(TenantName tenantName) {
        this.curator = null;
        this.remoteSessionFactory = null;
        this.reloadHandler = null;
        this.sessionsPath = TenantRepository.getSessionsPath(tenantName);
        this.metrics = null;
        this.directoryCache = null;
        this.applicationRepo = null;
    }

    //---------- START overrides to keep sessions changed in sync 

    @Override
    public synchronized void addSession(RemoteSession session) {
        super.addSession(session);
        sessionAdded(session.getSessionId());
    }

    @Override
    public synchronized void removeSessionOrThrow(long id) {
        super.removeSessionOrThrow(id);
        sessionRemoved(id);
    }

    /**
     * Removes a session
     *
     * @param id the id of the session to remove
     * @return the removed session, or null if none was found
     */
    @Override
    public synchronized RemoteSession removeSession(long id) { 
        RemoteSession session = super.removeSession(id);
        sessionRemoved(id);
        return session;
    }

    @Override
    public void removeSession(long id, NestedTransaction transaction) {
        super.removeSession(id, transaction);
        transaction.onCommitted(() -> sessionRemoved(id));
    }

    //---------- END overrides to keep sessions changed in sync 

    private void loadActiveSession(RemoteSession session) {
        tryReload(session.ensureApplicationLoaded(), session.logPre());
    }

    private void tryReload(ApplicationSet applicationSet, String logPre) {
        try {
            reloadHandler.reloadConfig(applicationSet);
            log.log(LogLevel.INFO, logPre+"Application activated successfully: " + applicationSet.getId());
        } catch (Exception e) {
            log.log(LogLevel.WARNING, logPre+"Skipping loading of application '" + applicationSet.getId() + "': " + Exceptions.toMessageString(e));
        }
    }

    private List<Long> getSessionListFromDirectoryCache(List<ChildData> children) {
        return getSessionList(children.stream()
                                      .map(child -> Path.fromString(child.getPath()).getName())
                                      .collect(Collectors.toList()));
    }

    private List<Long> getSessionList(List<String> children) {
        return children.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    // TODO: Add sessions in parallel
    private void initializeSessions() throws NumberFormatException {
        getSessionList(curator.getChildren(sessionsPath)).forEach(this::sessionAdded);
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
        try {
            log.log(LogLevel.DEBUG, "Adding session to RemoteSessionRepo: " + sessionId);
            RemoteSession session = remoteSessionFactory.createSession(sessionId);
            Path sessionPath = sessionsPath.append(String.valueOf(sessionId));
            Curator.FileCache fileCache = curator.createFileCache(sessionPath.append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH).getAbsolute(), false);
            fileCache.addListener(this);
            loadSessionIfActive(session);
            sessionStateWatchers.put(sessionId, new SessionStateWatcher(fileCache, reloadHandler, session, metrics));
            internalAddSession(session);
            metrics.incAddedSessions();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed loading session " + sessionId + ": No config for this session can be served", e);
        }
    }

    private void sessionRemoved(long sessionId) {
        SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        watcher.close();
        internalRemoveSessionOrThrow(sessionId);
        metrics.incRemovedSessions();
    }

    private void loadSessionIfActive(RemoteSession session) {
        for (ApplicationId applicationId : applicationRepo.listApplications()) {
            try {
                if (applicationRepo.getSessionIdForApplication(applicationId) == session.getSessionId()) {
                    log.log(LogLevel.DEBUG, "Found active application for session " + session.getSessionId() + " , loading it");
                    loadActiveSession(session);
                    break;
                }
            } catch (Exception e) {
                log.log(LogLevel.WARNING, session.logPre() + " error reading session id for " + applicationId);
            }
        }
    }

    public synchronized void close() {
        try {
            if (directoryCache != null) {
                directoryCache.close();
            }
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception when closing path cache", e);
        } finally {
            checkForRemovedSessions(new ArrayList<>());
        }
    }

    @Override
    public void nodeChanged() {
        Multiset<Session.Status> sessionMetrics = HashMultiset.create();
        for (RemoteSession session : listSessions()) {
            sessionMetrics.add(session.getStatus());
        }
        metrics.setNewSessions(sessionMetrics.count(Session.Status.NEW));
        metrics.setPreparedSessions(sessionMetrics.count(Session.Status.PREPARE));
        metrics.setActivatedSessions(sessionMetrics.count(Session.Status.ACTIVATE));
        metrics.setDeactivatedSessions(sessionMetrics.count(Session.Status.DEACTIVATE));
    }

    @Override
    public void childEvent(CuratorFramework framework, PathChildrenCacheEvent event) {
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Got child event: " + event);
        }
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
    }

    private void synchronizeOnNew(List<Long> sessionList) {
        for (long sessionId : sessionList) {
            RemoteSession session = getSession(sessionId);
            if (session == null) continue; // session might have been deleted after getting session list
            log.log(LogLevel.DEBUG, session.logPre() + "Confirming upload for session " + sessionId);
            session.confirmUpload();
        }
    }
}
