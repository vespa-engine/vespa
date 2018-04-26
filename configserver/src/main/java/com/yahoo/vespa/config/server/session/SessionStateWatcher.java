// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.log.LogLevel;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Watches one particular session (/vespa/config/apps/n/sessionState in ZK)
 * The session must be in the session repo.
 *
 * @author vegardh
 */
public class SessionStateWatcher implements NodeCacheListener {

    private static final Logger log = Logger.getLogger(SessionStateWatcher.class.getName());
    private final Curator.FileCache fileCache;
    private final ReloadHandler reloadHandler;
    private final RemoteSession session;
    private final MetricUpdater metrics;
    private final Executor executor;

    public SessionStateWatcher(Curator.FileCache fileCache,
                               ReloadHandler reloadHandler,
                               RemoteSession session,
                               MetricUpdater metrics) {
        executor = Executors.newSingleThreadExecutor(
                ThreadFactoryFactory.getThreadFactory(SessionStateWatcher.class.getName() + "-" + session));
        this.fileCache = fileCache;
        this.reloadHandler = reloadHandler;
        this.session = session;
        this.metrics = metrics;
        this.fileCache.start();
        this.fileCache.addListener(this);
    }

    private void sessionChanged(Session.Status status) {
        log.log(LogLevel.DEBUG, session.logPre()+"Session change: Session " + session.getSessionId() + " changed status to " + status);

        // valid for NEW -> PREPARE transitions, not ACTIVATE -> PREPARE.
        if (status.equals(Session.Status.PREPARE)) {
            log.log(LogLevel.DEBUG, session.logPre() + "Loading prepared session: " + session.getSessionId());
            session.loadPrepared();
        } else if (status.equals(Session.Status.ACTIVATE)) {
            session.makeActive(reloadHandler);
        } else if (status.equals(Session.Status.DEACTIVATE)) {
            session.deactivate();
        }
    }

    public long getSessionId() {
        return session.getSessionId();
    }

    public void close() {
        try {
            fileCache.close();
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception when closing watcher", e);
        }
    }

    @Override
    public void nodeChanged() {
        executor.execute(() -> {
            try {
                ChildData data = fileCache.getCurrentData();
                if (data != null) {
                    sessionChanged(Session.Status.parse(Utf8.toString(fileCache.getCurrentData().getData())));
                }
            } catch (Exception e) {
                log.log(LogLevel.WARNING, session.logPre() + "Error handling session changed for session " + getSessionId(), e);
                metrics.incSessionChangeErrors();
            }
        });
    }

}
