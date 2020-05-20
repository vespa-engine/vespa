// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.lang.SettableOptional;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.modelfactory.ActivatedModelsBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import org.apache.zookeeper.KeeperException;

import java.time.Clock;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A RemoteSession represents a session created on another config server. This session can
 * be regarded as read only, and this interface only allows reading information about a session.
 *
 * @author Ulf Lilleengen
 */
public class RemoteSession extends Session {

    private static final Logger log = Logger.getLogger(RemoteSession.class.getName());
    private ApplicationSet applicationSet = null;
    private final ActivatedModelsBuilder applicationLoader;
    private final Clock clock;

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param tenant The name of the tenant creating session
     * @param sessionId The session id for this session.
     * @param componentRegistry a registry of global components
     * @param zooKeeperClient a SessionZooKeeperClient instance
     */
    public RemoteSession(TenantName tenant,
                         long sessionId,
                         GlobalComponentRegistry componentRegistry,
                         SessionZooKeeperClient zooKeeperClient) {
        super(tenant, sessionId, zooKeeperClient);
        this.applicationLoader = new ActivatedModelsBuilder(tenant, sessionId, zooKeeperClient, componentRegistry);
        this.clock = componentRegistry.getClock();
    }

    void loadPrepared() {
        Curator.CompletionWaiter waiter = zooKeeperClient.getPrepareWaiter();
        ensureApplicationLoaded();
        notifyCompletion(waiter);
    }

    private ApplicationSet loadApplication() {
        ApplicationPackage applicationPackage = zooKeeperClient.loadApplicationPackage();

        // Read hosts allocated on the config server instance which created this
        Optional<AllocatedHosts> allocatedHosts = applicationPackage.getAllocatedHosts();

        return ApplicationSet.fromList(applicationLoader.buildModels(zooKeeperClient.readApplicationId(),
                                                                     zooKeeperClient.readDockerImageRepository(),
                                                                     zooKeeperClient.readVespaVersion(),
                                                                     applicationPackage,
                                                                     new SettableOptional<>(allocatedHosts),
                                                                     clock.instant()));
    }

    public synchronized ApplicationSet ensureApplicationLoaded() {
        return applicationSet == null ? applicationSet = loadApplication() : applicationSet;
    }

    public Session.Status getStatus() {
        return zooKeeperClient.readStatus();
    }

    public synchronized void deactivate() {
        applicationSet = null;
    }

    public Transaction createDeleteTransaction() {
        return zooKeeperClient.createWriteStatusTransaction(Status.DELETE);
    }

    void makeActive(ReloadHandler reloadHandler) {
        Curator.CompletionWaiter waiter = zooKeeperClient.getActiveWaiter();
        log.log(Level.FINE, () -> logPre() + "Getting session from repo: " + getSessionId());
        ApplicationSet app = ensureApplicationLoaded();
        log.log(Level.FINE, () -> logPre() + "Reloading config for " + getSessionId());
        reloadHandler.reloadConfig(app);
        log.log(Level.FINE, () -> logPre() + "Notifying " + waiter);
        notifyCompletion(waiter);
        log.log(Level.INFO, logPre() + "Session activated: " + getSessionId());
    }
    
    @Override
    public String logPre() {
        if (getApplicationId().equals(ApplicationId.defaultId())) {
            return TenantRepository.logPre(getTenant());
        } else {
            return TenantRepository.logPre(getApplicationId());
        }
    }

    void confirmUpload() {
        Curator.CompletionWaiter waiter = zooKeeperClient.getUploadWaiter();
        log.log(Level.FINE, "Notifying upload waiter for session " + getSessionId());
        notifyCompletion(waiter);
        log.log(Level.FINE, "Done notifying upload for session " + getSessionId());
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
                log.log(Level.INFO, "Not able to notify completion for session: " + getSessionId() + ", node has been deleted");
            }
        }
    }

    public void delete() {
        Transaction transaction = zooKeeperClient.deleteTransaction();
        transaction.commit();
        transaction.close();
    }

    public ApplicationId getApplicationId() { return zooKeeperClient.readApplicationId(); }

    public Optional<DockerImage> getDockerImageRepository() { return zooKeeperClient.readDockerImageRepository(); }

    public Version getVespaVersion() { return zooKeeperClient.readVespaVersion(); }

    public Optional<AthenzDomain> getAthenzDomain() { return zooKeeperClient.readAthenzDomain(); }

    public AllocatedHosts getAllocatedHosts() {
        return zooKeeperClient.getAllocatedHosts();
    }

    // Note: Assumes monotonically increasing session ids
    public boolean isNewerThan(long sessionId) {
        return getSessionId() > sessionId;
    }

    public Transaction createDeactivateTransaction() {
        return createSetStatusTransaction(Status.DEACTIVATE);
    }

    private Transaction createSetStatusTransaction(Status status) {
        return zooKeeperClient.createWriteStatusTransaction(status);
    }

    public ApplicationMetaData getMetaData() {
        return zooKeeperClient.loadApplicationPackage().getMetaData();
    }


}
