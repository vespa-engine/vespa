// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.cache.ChildData;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.session.Session.Status;

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
    private final RemoteSession remoteSession;
    private final MetricUpdater metrics;
    private final Executor zkWatcherExecutor;
    private final SessionRepository sessionRepository;

    SessionStateWatcher(Curator.FileCache fileCache,
                        RemoteSession remoteSession,
                        MetricUpdater metrics,
                        Executor zkWatcherExecutor,
                        SessionRepository sessionRepository) {
        this.fileCache = fileCache;
        this.remoteSession = remoteSession;
        this.metrics = metrics;
        this.fileCache.addListener(this::nodeChanged);
        this.fileCache.start();
        this.zkWatcherExecutor = zkWatcherExecutor;
        this.sessionRepository = sessionRepository;
    }

    private void sessionStatusChanged(Status newStatus) {
        long sessionId = remoteSession.getSessionId();
        switch (newStatus) {
            case NEW:
            case NONE:
                break;
            case PREPARE:
                createLocalSession(sessionId);
                sessionRepository.prepare(remoteSession);
                break;
            case ACTIVATE:
                createLocalSession(sessionId);
                sessionRepository.activate(remoteSession);
                break;
            case DEACTIVATE:
                sessionRepository.deactivate(remoteSession);
                break;
            case DELETE:
                sessionRepository.delete(remoteSession);
                break;
            default:
                throw new IllegalStateException("Unknown status " + newStatus);
        }
    }

    private void createLocalSession(long sessionId) {
        if (sessionRepository.distributeApplicationPackage()) {
            sessionRepository.createLocalSessionUsingDistributedApplicationPackage(sessionId);
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
            Status newStatus = Status.NONE;
            try {
                ChildData node = fileCache.getCurrentData();
                if (node != null) {
                    newStatus = Status.parse(Utf8.toString(node.getData()));
                    log.log(Level.FINE, remoteSession.logPre() + "Session change: Session "
                                        + remoteSession.getSessionId() + " changed status to " + newStatus.name());
                    sessionStatusChanged(newStatus);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, remoteSession.logPre() + "Error handling session change to " +
                                       newStatus.name() + " for session " + getSessionId(), e);
                metrics.incSessionChangeErrors();
            }
        });
    }

}
