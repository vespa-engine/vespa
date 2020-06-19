// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import java.util.Optional;
import java.util.logging.Level;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.cache.ChildData;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Watches one particular session (/config/v2/tenants/&lt;tenantName&gt;/sessions/&lt;n&gt;/sessionState in ZooKeeper)
 * The session must be in the session repo.
 *
 * @author Vegard Havdal
 * @author hmusum
 */
public class SessionStateWatcher {

    private static final Logger log = Logger.getLogger(SessionStateWatcher.class.getName());

    private final Curator.FileCache fileCache;
    private final ReloadHandler reloadHandler;
    private final RemoteSession remoteSession;
    private final Optional<LocalSession> localSession;
    private final MetricUpdater metrics;
    private final Executor zkWatcherExecutor;
    private final SessionRepository sessionRepository;

    SessionStateWatcher(Curator.FileCache fileCache,
                        ReloadHandler reloadHandler,
                        RemoteSession remoteSession,
                        Optional<LocalSession> localSession,
                        MetricUpdater metrics,
                        Executor zkWatcherExecutor,
                        SessionRepository sessionRepository) {
        this.fileCache = fileCache;
        this.reloadHandler = reloadHandler;
        this.remoteSession = remoteSession;
        this.localSession = localSession;
        this.metrics = metrics;
        this.fileCache.start();
        this.fileCache.addListener(this::nodeChanged);
        this.zkWatcherExecutor = zkWatcherExecutor;
        this.sessionRepository = sessionRepository;
    }

    private void sessionChanged(Session.Status status) {
        long sessionId = remoteSession.getSessionId();

        // valid for NEW -> PREPARE transitions, not ACTIVATE -> PREPARE.
        if (status.equals(Session.Status.PREPARE)) {
            log.log(Level.FINE, remoteSession.logPre() + "Loading prepared session: " + remoteSession.getSessionId());
            remoteSession.loadPrepared();
        } else if (status.equals(Session.Status.ACTIVATE)) {
            remoteSession.makeActive(reloadHandler);
        } else if (status.equals(Session.Status.DEACTIVATE)) {
            remoteSession.deactivate();
        } else if (status.equals(Session.Status.DELETE)) {
            remoteSession.deactivate();
            log.log(Level.INFO, remoteSession.logPre() + "Session change: Local session " + sessionId + " changed status to " + status);
            localSession.ifPresent(localSession1 -> {
                 log.log(Level.FINE, remoteSession.logPre() + "Deleting session " + sessionId);
                 sessionRepository.deleteLocalSession(localSession.get());
             });
        }
    }

    public long getSessionId() {
        return remoteSession.getSessionId();
    }

    public void close() {
        try {
            fileCache.close();
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception when closing watcher", e);
        }
    }

    private void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            Session.Status currentStatus = remoteSession.getStatus();
            Session.Status newStatus = Session.Status.NONE;
            try {
                ChildData node = fileCache.getCurrentData();
                if (node != null) {
                    newStatus = Session.Status.parse(Utf8.toString(node.getData()));
                    log.log(Level.FINE, remoteSession.logPre() + "Session change: Remote session " + remoteSession.getSessionId() +
                                        " changed status from " + currentStatus.name() + " to " + newStatus.name());
                    sessionChanged(newStatus);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, remoteSession.logPre() + "Error handling session change from " + currentStatus.name() +
                                       " to " + newStatus.name() + " for session " + getSessionId(), e);
                metrics.incSessionChangeErrors();
            }
        });
    }

}
