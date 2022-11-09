// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.cache.ChildData;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.session.Session.Status;

/**
 * Watches session state for a session (/config/v2/tenants/&lt;tenantName&gt;/sessions/&lt;n&gt;/sessionState in ZooKeeper)
 * The session must be in the session repo.
 *
 * @author Vegard Havdal
 * @author hmusum
 */
public class SessionStateWatcher {

    private static final Logger log = Logger.getLogger(SessionStateWatcher.class.getName());

    private final Curator.FileCache fileCache;
    private final long sessionId;
    private final MetricUpdater metrics;
    private final Executor zkWatcherExecutor;
    private final SessionRepository sessionRepository;

    SessionStateWatcher(Curator.FileCache fileCache,
                        long sessionId,
                        MetricUpdater metrics,
                        Executor zkWatcherExecutor,
                        SessionRepository sessionRepository) {
        this.fileCache = fileCache;
        this.sessionId = sessionId;
        this.metrics = metrics;
        this.fileCache.addListener(this::nodeChanged);
        this.fileCache.start();
        this.zkWatcherExecutor = zkWatcherExecutor;
        this.sessionRepository = sessionRepository;
    }

    private synchronized void sessionStatusChanged(Status newStatus) {
        switch (newStatus) {
            case NEW:
            case UNKNOWN:
                break;
            case DELETE:
            case DEACTIVATE:
                sessionRepository.deactivateSession(sessionId);
                break;
            case PREPARE:
                sessionRepository.prepareRemoteSession(sessionId);
                break;
            case ACTIVATE:
                sessionRepository.activate(sessionId);
                break;
            default:
                throw new IllegalStateException("Unknown status " + newStatus);
        }
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
            Status newStatus = Status.UNKNOWN;
            try {
                ChildData node = fileCache.getCurrentData();
                if (node != null) {
                    newStatus = Status.parse(Utf8.toString(node.getData()));
                    sessionStatusChanged(newStatus);
                }
            } catch (NotFoundException e) {
                log.log(Level.INFO, "Session or application not found when handling session change to " + newStatus.name() + " for session " + sessionId);
                metrics.incSessionChangeErrors();
            } catch (Exception e) {
                log.log(Level.WARNING, "Error handling session change to " + newStatus.name() + " for session " + sessionId, e);
                metrics.incSessionChangeErrors();
            }
        });
    }

}
