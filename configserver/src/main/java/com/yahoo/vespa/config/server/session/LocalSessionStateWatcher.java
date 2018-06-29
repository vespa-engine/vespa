// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.log.LogLevel;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Watches one particular local session (/config/v2/tenants/&lt;tenantName&gt;/sessions/&lt;n&gt;/sessionState in ZooKeeper)
 * to pick up when an application is deleted (the delete might be done on any config server in the cluster)
 *
 * @author Harald Musum
 */
public class LocalSessionStateWatcher implements NodeCacheListener {

    private static final Logger log = Logger.getLogger(LocalSessionStateWatcher.class.getName());
    // One thread pool for all instances of this class
    private static final Executor executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory(LocalSessionStateWatcher.class.getName()));

    private final Curator.FileCache fileCache;
    private final LocalSession session;
    private final LocalSessionRepo localSessionRepo;

    LocalSessionStateWatcher(Curator.FileCache fileCache, LocalSession session, LocalSessionRepo localSessionRepo) {
        this.fileCache = fileCache;
        this.session = session;
        this.localSessionRepo = localSessionRepo;
        this.fileCache.start();
        this.fileCache.addListener(this);
    }

    // Will delete session if it exists in local session repo
    private void sessionChanged(Session.Status status) {
        long sessionId = session.getSessionId();
        log.log(LogLevel.DEBUG, session.logPre() + "Session change: Local session " + sessionId + " changed status to " + status);

        if (status.equals(Session.Status.DELETE) && localSessionRepo.getSession(sessionId) != null) {
            log.log(LogLevel.DEBUG, session.logPre() + "Deleting session " + sessionId);
            localSessionRepo.deleteSession(session);
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
            }
        });
    }

}
