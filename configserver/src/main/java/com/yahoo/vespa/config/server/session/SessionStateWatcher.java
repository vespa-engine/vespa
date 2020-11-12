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
    private volatile RemoteSession session;
    private final MetricUpdater metrics;
    private final Executor zkWatcherExecutor;
    private final SessionRepository sessionRepository;

    SessionStateWatcher(Curator.FileCache fileCache,
                        RemoteSession session,
                        MetricUpdater metrics,
                        Executor zkWatcherExecutor,
                        SessionRepository sessionRepository) {
        this.fileCache = fileCache;
        this.session = session;
        this.metrics = metrics;
        this.fileCache.addListener(this::nodeChanged);
        this.fileCache.start();
        this.zkWatcherExecutor = zkWatcherExecutor;
        this.sessionRepository = sessionRepository;
    }

    private synchronized void sessionStatusChanged(Status newStatus) {
        long sessionId = session.getSessionId();
        switch (newStatus) {
            case NEW:
            case NONE:
                break;
            case DELETE:
                sessionRepository.deactivateAndUpdateCache(session);
                break;
            case PREPARE:
                createLocalSession(sessionId);
                sessionRepository.prepareRemoteSession(session);
                break;
            case ACTIVATE:
                createLocalSession(sessionId);
                sessionRepository.activate(session);
                break;
            case DEACTIVATE:
                sessionRepository.deactivateAndUpdateCache(session);
                break;
            default:
                throw new IllegalStateException("Unknown status " + newStatus);
        }
    }

    private void createLocalSession(long sessionId) {
        sessionRepository.createLocalSessionFromDistributedApplicationPackage(sessionId);
    }

    public long getSessionId() {
        return session.getSessionId();
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
                    log.log(Level.FINE, session.logPre() + "Session change: Session "
                                        + session.getSessionId() + " changed status to " + newStatus.name());
                    sessionStatusChanged(newStatus);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, session.logPre() + "Error handling session change to " +
                                       newStatus.name() + " for session " + getSessionId(), e);
                metrics.incSessionChangeErrors();
            }
        });
    }

    public synchronized void updateRemoteSession(RemoteSession session) {
        this.session = session;
    }

}
