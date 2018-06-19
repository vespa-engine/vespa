// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.*;
import com.yahoo.lang.SettableOptional;
import com.yahoo.vespa.config.server.*;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.modelfactory.ActivatedModelsBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import org.apache.zookeeper.KeeperException;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * A RemoteSession represents a session created on another config server. This session can
 * be regarded as read only, and this interface only allows reading information about a session.
 *
 * @author Ulf Lilleengen
 */
public class RemoteSession extends Session {

    private static final Logger log = Logger.getLogger(RemoteSession.class.getName());
    private volatile ApplicationSet applicationSet = null;
    private final ActivatedModelsBuilder applicationLoader;
    private final Clock clock;

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param tenant The name of the tenant creating session
     * @param sessionId The session id for this session.
     * @param globalComponentRegistry a registry of global components
     * @param zooKeeperClient a SessionZooKeeperClient instance
     */
    public RemoteSession(TenantName tenant,
                         long sessionId,
                         GlobalComponentRegistry globalComponentRegistry,
                         SessionZooKeeperClient zooKeeperClient,
                         Clock clock) {
        super(tenant, sessionId, zooKeeperClient);
        this.applicationLoader = new ActivatedModelsBuilder(tenant, sessionId, zooKeeperClient, globalComponentRegistry);
        this.clock = clock;
    }

    public void loadPrepared() {
        Curator.CompletionWaiter waiter = zooKeeperClient.getPrepareWaiter();
        ensureApplicationLoaded();
        notifyCompletion(waiter);
    }

    private ApplicationSet loadApplication() {
        ApplicationPackage applicationPackage = zooKeeperClient.loadApplicationPackage();

        // Read hosts allocated on the config server instance which created this
        Optional<AllocatedHosts> allocatedHosts = applicationPackage.getAllocatedHosts();

        return ApplicationSet.fromList(applicationLoader.buildModels(zooKeeperClient.readApplicationId(),
                                                                     zooKeeperClient.readVespaVersion(),
                                                                     zooKeeperClient.loadApplicationPackage(),
                                                                     new SettableOptional<>(allocatedHosts),
                                                                     clock.instant()));
    }

    public ApplicationSet ensureApplicationLoaded() {
        if (applicationSet == null) {
            applicationSet = loadApplication();
        }
        return applicationSet;
    }

    public Session.Status getStatus() {
        return zooKeeperClient.readStatus();
    }

    public void deactivate() {
        applicationSet = null;
    }

    public void makeActive(ReloadHandler reloadHandler) {
        Curator.CompletionWaiter waiter = zooKeeperClient.getActiveWaiter();
        log.log(LogLevel.DEBUG, logPre()+"Getting session from repo: " + getSessionId());
        ApplicationSet app = ensureApplicationLoaded();
        log.log(LogLevel.DEBUG, logPre() + "Reloading config for " + app);
        reloadHandler.reloadConfig(app);
        log.log(LogLevel.DEBUG, logPre() + "Notifying " + waiter);
        notifyCompletion(waiter);
        log.log(LogLevel.DEBUG, logPre() + "Session activated: " + app);
    }
    
    @Override
    public String logPre() {
        if (applicationSet != null) {
            return TenantRepository.logPre(applicationSet.getForVersionOrLatest(Optional.empty(), Instant.now()).getId());
        }

        return TenantRepository.logPre(getTenant());
    }

    public void confirmUpload() {
        Curator.CompletionWaiter waiter = zooKeeperClient.getUploadWaiter();
        log.log(LogLevel.DEBUG, "Notifying upload waiter for session " + getSessionId());
        notifyCompletion(waiter);
        log.log(LogLevel.DEBUG, "Done notifying for session " + getSessionId());
    }

    private void notifyCompletion(Curator.CompletionWaiter completionWaiter) {
        try {
            completionWaiter.notifyCompletion();
        } catch (RuntimeException e) {
            // Throw only if we get something else than NoNodeException -- NoNodeException might happen when
            // the session is no longer in use (e.g. the app using this session has been deleted) and this method
            // has not been called yet for the previous session operation
            // on a minority of the config servers (see awaitInternal() method in this class)
            if (e.getCause().getClass() != KeeperException.NoNodeException.class) {
                throw e;
            } else {
                log.log(LogLevel.INFO, "Not able to notify completion for session: " + getSessionId() + ", node has been deleted");
            }
        }
    }

}
