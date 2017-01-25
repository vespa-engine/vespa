// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.Deployment;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionFactory;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.ActivateLock;
import com.yahoo.vespa.config.server.tenant.Rotations;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The API for managing applications.
 *
 * @author bratseth
 */
// TODO: Move logic for dealing with applications here from the HTTP layer and make this the persistent component
//       owning the rest of the state
public class ApplicationRepository implements com.yahoo.config.provision.Deployer {

    private static final Logger log = Logger.getLogger(ApplicationRepository.class.getName());

    private final Tenants tenants;
    private final Optional<Provisioner> hostProvisioner;
    private final Curator curator;
    private final LogServerLogGrabber logServerLogGrabber;
    private final ApplicationConvergenceChecker convergeChecker;
    private final Clock clock;
    private final DeployLogger logger = new SilentDeployLogger();

    public ApplicationRepository(Tenants tenants,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 Curator curator,
                                 LogServerLogGrabber logServerLogGrabber,
                                 ApplicationConvergenceChecker applicationConvergenceChecker) {
        this.tenants = tenants;
        this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
        this.curator = curator;
        this.logServerLogGrabber = logServerLogGrabber;
        this.convergeChecker = applicationConvergenceChecker;
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
        Tenant tenant = tenants.getTenant(application.tenant());
        LocalSession activeSession = tenant.getLocalSessionRepo().getActiveSession(application);
        if (activeSession == null) return Optional.empty();
        TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);
        LocalSession newSession = tenant.getSessionFactory().createSessionFromExisting(activeSession, logger, timeoutBudget);
        tenant.getLocalSessionRepo().addSession(newSession);
        return Optional.of(Deployment.unprepared(newSession,
                                                 tenant.getLocalSessionRepo(),
                                                 tenant.getPath(),
                                                 hostProvisioner,
                                                 new ActivateLock(curator, tenant.getPath()),
                                                 timeout,
                                                 clock,
                                                 /* already deployed, validate: */ false));
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
     * @throws RuntimeException if the remove transaction fails. This method is exception safe.
     */
    public boolean remove(ApplicationId applicationId) {
        Optional<Tenant> owner = Optional.ofNullable(tenants.getTenant(applicationId.tenant()));
        if ( ! owner.isPresent()) return false;

        TenantApplications tenantApplications = owner.get().getApplicationRepo();
        if ( ! tenantApplications.listApplications().contains(applicationId)) return false;

        // TODO: Push lookup logic down
        long sessionId = tenantApplications.getSessionIdForApplication(applicationId);
        LocalSessionRepo localSessionRepo = owner.get().getLocalSessionRepo();
        LocalSession session = localSessionRepo.getSession(sessionId);
        if (session == null) return false;

        NestedTransaction transaction = new NestedTransaction();
        localSessionRepo.removeSession(session.getSessionId(), transaction);
        session.delete(transaction); // TODO: Not unit tested

        transaction.add(new Rotations(owner.get().getCurator(), owner.get().getPath()).delete(applicationId)); // TODO: Not unit tested
        // (When rotations are updated in zk, we need to redeploy the zone app, on the right config server
        // this is done asynchronously in application maintenance by the node repository)

        transaction.add(tenantApplications.deleteApplication(applicationId));

        hostProvisioner.ifPresent(provisioner -> provisioner.remove(transaction, applicationId));
        transaction.onCommitted(() -> log.log(LogLevel.INFO, "Deleted " + applicationId));
        transaction.commit();

        return true;
    }

    public String grabLog(Tenant tenant, ApplicationId applicationId) {
        Application application = getApplication(tenant, applicationId);
        return logServerLogGrabber.grabLog(application);
    }

    public HttpResponse nodeConvergenceCheck(Tenant tenant, ApplicationId applicationId, String hostname, URI uri) {
        Application application = getApplication(tenant, applicationId);
        return convergeChecker.nodeConvergenceCheck(application, hostname, uri);
    }

    public HttpResponse listConfigConvergence(Tenant tenant, ApplicationId applicationId, URI uri) {
        Application application = getApplication(tenant, applicationId);
        return convergeChecker.listConfigConvergence(application, uri);
    }

    public Long getApplicationGeneration(Tenant tenant, ApplicationId applicationId) {
        return getApplication(tenant, applicationId).getApplicationGeneration();
    }

    private Application getApplication(Tenant tenant, ApplicationId applicationId) {
        long sessionId = getSessionIdForApplication(tenant, applicationId);
        RemoteSession session = tenant.getRemoteSessionRepo().getSession(sessionId, 0);
        return session.ensureApplicationLoaded().getForVersionOrLatest(Optional.empty());
    }

    public long getSessionIdForApplication(Tenant tenant, ApplicationId applicationId) {
        return tenant.getApplicationRepo().getSessionIdForApplication(applicationId);
    }

    private LocalSession getLocalSession(Tenant tenant, long sessionId) {
        LocalSession session = tenant.getLocalSessionRepo().getSession(sessionId);
        if (session == null) throw new NotFoundException("Session " + sessionId + " was not found");

        return session;
    }

    private RemoteSession getRemoteSession(Tenant tenant, long sessionId) {
        RemoteSession session = tenant.getRemoteSessionRepo().getSession(sessionId);
        if (session == null) throw new NotFoundException("Session " + sessionId + " was not found");

        return session;
    }

    public void restart(ApplicationId applicationId, HostFilter hostFilter) {
        hostProvisioner.ifPresent(provisioner -> provisioner.restart(applicationId, hostFilter));
    }

    public Tenant verifyTenantAndApplication(ApplicationId applicationId) {
        TenantName tenantName = applicationId.tenant();
        if (!tenants.checkThatTenantExists(tenantName)) {
            throw new IllegalArgumentException("Tenant " + tenantName + " was not found.");
        }
        Tenant tenant = tenants.getTenant(tenantName);
        List<ApplicationId> applicationIds = listApplicationIds(tenant);
        if (!applicationIds.contains(applicationId)) {
            throw new IllegalArgumentException("No such application id: " + applicationId);
        }
        return tenant;
    }

    public ApplicationId activate(Tenant tenant,
                                  long sessionId,
                                  TimeoutBudget timeoutBudget,
                                  boolean ignoreLockFailure,
                                  boolean ignoreSessionStaleFailure) {
        LocalSession localSession = getLocalSession(tenant, sessionId);
        LocalSessionRepo localSessionRepo = tenant.getLocalSessionRepo();
        ActivateLock activateLock = tenant.getActivateLock();
        // TODO: Get rid of the activateLock and localSessionRepo arguments in deployFromPreparedSession
        Deployment deployment = deployFromPreparedSession(localSession,
                                                          activateLock,
                                                          localSessionRepo,
                                                          timeoutBudget.timeLeft());
        deployment.setIgnoreLockFailure(ignoreLockFailure);
        deployment.setIgnoreSessionStaleFailure(ignoreSessionStaleFailure);
        deployment.activate();
        return localSession.getApplicationId();
    }

    public ApplicationMetaData getMetadataFromSession(Tenant tenant, long sessionId) {
        return getLocalSession(tenant, sessionId).getMetaData();
    }

    public void validateThatLocalSessionIsNotActive(Tenant tenant, long sessionId) {
        LocalSession session = getLocalSession(tenant, sessionId);
        if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalStateException("Session is active: " + sessionId);
        }
    }

    public void validateThatRemoteSessionIsNotActive(Tenant tenant, long sessionId) {
        RemoteSession session = getRemoteSession(tenant, sessionId);
        if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalStateException("Session is active: " + sessionId);
        }
    }

    public void validateThatRemoteSessionIsPrepared(Tenant tenant, long sessionId) {
        RemoteSession session = getRemoteSession(tenant, sessionId);
        if (!Session.Status.PREPARE.equals(session.getStatus()))
            throw new IllegalStateException("Session not prepared: " + sessionId);
    }

    private Optional<ApplicationSet> getCurrentActiveApplicationSet(Tenant tenant, ApplicationId appId) {
        Optional<ApplicationSet> currentActiveApplicationSet = Optional.empty();
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        try {
            long currentActiveSessionId = applicationRepo.getSessionIdForApplication(appId);
            RemoteSession currentActiveSession = getRemoteSession(tenant, currentActiveSessionId);
            if (currentActiveSession != null) {
                currentActiveApplicationSet = Optional.ofNullable(currentActiveSession.ensureApplicationLoaded());
            }
        } catch (IllegalArgumentException e) {
            // Do nothing if we have no currently active session
        }
        return currentActiveApplicationSet;
    }

    public ConfigChangeActions prepare(Tenant tenant,
                                       long sessionId,
                                       DeployLogger logger,
                                       PrepareParams params) {
        LocalSession session = getLocalSession(tenant, sessionId);
        ApplicationId appId = params.getApplicationId();
        Optional<ApplicationSet> currentActiveApplicationSet = getCurrentActiveApplicationSet(tenant, appId);
        return session.prepare(logger,
                               params,
                               currentActiveApplicationSet,
                               tenant.getPath());
    }

    private List<ApplicationId> listApplicationIds(Tenant tenant) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return applicationRepo.listApplications();
    }

    public long createSessionFromExisting(Tenant tenant, DeployLogger logger,
                                          TimeoutBudget timeoutBudget, ApplicationId applicationId) {
        LocalSessionRepo localSessionRepo = tenant.getLocalSessionRepo();
        SessionFactory sessionFactory = tenant.getSessionFactory();
        LocalSession fromSession = getExistingSession(tenant, applicationId);
        LocalSession session = sessionFactory.createSessionFromExisting(fromSession, logger, timeoutBudget);
        localSessionRepo.addSession(session);
        return session.getSessionId();
    }

    public long createSession(Tenant tenant, TimeoutBudget timeoutBudget, File applicationDirectory, String applicationName) {
        LocalSessionRepo localSessionRepo = tenant.getLocalSessionRepo();
        SessionFactory sessionFactory = tenant.getSessionFactory();
        LocalSession session = sessionFactory.createSession(applicationDirectory, applicationName, timeoutBudget);
        localSessionRepo.addSession(session);
        return session.getSessionId();
    }

    public ApplicationFile getApplicationFileFromSession(TenantName tenantName, long sessionId, String path, LocalSession.Mode mode) {
        Tenant tenant = tenants.getTenant(tenantName);
        return getLocalSession(tenant, sessionId).getApplicationFile(Path.fromString(path), mode);
    }

    private LocalSession getExistingSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return getLocalSession(tenant, applicationRepo.getSessionIdForApplication(applicationId));
    }

}
