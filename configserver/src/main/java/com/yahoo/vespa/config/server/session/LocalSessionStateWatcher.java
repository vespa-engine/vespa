// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.log.LogLevel;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.recipes.cache.ChildData;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Watches one particular local session (/config/v2/tenants/&lt;tenantName&gt;/sessions/&lt;n&gt;/sessionState in ZooKeeper)
 * to pick up when an application is deleted (the delete might be done on any config server in the cluster)
 *
 * @author Harald Musum
 */
public class LocalSessionStateWatcher {

    private static final Logger log = Logger.getLogger(LocalSessionStateWatcher.class.getName());

    private final Curator.FileCache fileCache;
    private final LocalSession session;
    private final LocalSessionRepo localSessionRepo;
    private final Executor zkWatcherExecutor;

    LocalSessionStateWatcher(Curator.FileCache fileCache, LocalSession session,
                             LocalSessionRepo localSessionRepo, Executor zkWatcherExecutor) {
        this.fileCache = fileCache;
        this.session = session;
        this.localSessionRepo = localSessionRepo;
        this.zkWatcherExecutor = zkWatcherExecutor;
        this.fileCache.start();
        this.fileCache.addListener(this::nodeChanged);
    }

    // Will delete session if it exists in local session repo
    private void sessionChanged(Session.Status status) {
        long sessionId = session.getSessionId();
        log.log(status == Session.Status.DELETE ? LogLevel.INFO : LogLevel.DEBUG,
                session.logPre() + "Session change: Local session " + sessionId + " changed status to " + status);

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

    public void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            try {
                ChildData node = fileCache.getCurrentData();
                if (node != null) {
                    sessionChanged(Session.Status.parse(Utf8.toString(node.getData())));
                }
            } catch (Exception e) {
                log.log(LogLevel.WARNING, session.logPre() + "Error handling session changed for session " + getSessionId(), e);
            }
        });
    }

}
