// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.ActivateLock;
import com.yahoo.vespa.config.server.Rotations;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.Tenants;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationRepo;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.curator.Curator;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The API for deploying applications.
 * A class which needs to deploy applications can have an instance of this injected.
 *
 * @author bratseth
 */
public class Deployer implements com.yahoo.config.provision.Deployer {

    private static final Logger log = Logger.getLogger(Deployer.class.getName());
    
    private final Tenants tenants;
    private final Optional<Provisioner> hostProvisioner;
    private final ConfigserverConfig configserverConfig;
    private final Curator curator;
    private final Clock clock;
    private final DeployLogger logger = new SilentDeployLogger();

    public Deployer(Tenants tenants, HostProvisionerProvider hostProvisionerProvider,
                    ConfigserverConfig configserverConfig, Curator curator) {
        this.tenants = tenants;
        this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
        this.configserverConfig = configserverConfig;
        this.curator = curator;
        this.clock = Clock.systemUTC();
    }

    /**
     * Creates a new deployment from the active application, if available.
     *
     * @param application the active application to be redeployed
     * @param timeout the timeout to use for each individual deployment operation
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    @Override
    public Optional<com.yahoo.config.provision.Deployment> deployFromLocalActive(ApplicationId application, Duration timeout) {
        Tenant tenant = tenants.tenantsCopy().get(application.tenant());
        LocalSession activeSession = tenant.getLocalSessionRepo().getActiveSession(application);
        if (activeSession == null) return Optional.empty();
        TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);
        LocalSession newSession = tenant.getSessionFactory().createSessionFromExisting(activeSession, logger, timeoutBudget);
        tenant.getLocalSessionRepo().addSession(newSession);
        return Optional.of(Deployment.unprepared(newSession,
                                                 tenant.getLocalSessionRepo(),
                                                 tenant.getPath(),
                                                 configserverConfig,
                                                 hostProvisioner,
                                                 new ActivateLock(curator, tenant.getPath()),
                                                 timeout, clock));
    }

    public Deployment deployFromPreparedSession(LocalSession session, ActivateLock lock, LocalSessionRepo localSessionRepo, Duration timeout) {
        return Deployment.prepared(session,
                                   localSessionRepo,
                                   hostProvisioner,
                                   lock,
                                   timeout, clock);
    }

    /** 
     * Removes a previously deployed application
     * 
     * @return true if the application was found and removed, false if it was not present
     */
    public boolean remove(ApplicationId applicationId) {
        Optional<Tenant> owner = Optional.ofNullable(tenants.tenantsCopy().get(applicationId.tenant()));
        if ( ! owner.isPresent()) return false;

        ApplicationRepo applicationRepo = owner.get().getApplicationRepo();
        if ( ! applicationRepo.listApplications().contains(applicationId)) return false;
        
        // TODO: Push lookup logic down
        long sessionId = applicationRepo.getSessionIdForApplication(applicationId);
        LocalSessionRepo localSessionRepo = owner.get().getLocalSessionRepo();
        LocalSession session = localSessionRepo.getSession(sessionId);
        if (session == null) return false;

        NestedTransaction transaction = new NestedTransaction();
        transaction.add(localSessionRepo.removeSessionTransaction(session.getSessionId()));
        session.delete(transaction); // TODO: Not tested

        transaction.add(new Rotations(owner.get().getCurator(), owner.get().getPath()).delete(applicationId)); // TODO: Not tested

        transaction.add(applicationRepo.deleteApplication(applicationId));

        transaction.commit();

        if (hostProvisioner.isPresent())
            hostProvisioner.get().removed(applicationId);
        log.log(LogLevel.INFO, "Deleted " + applicationId);
        return true;
    }

}
