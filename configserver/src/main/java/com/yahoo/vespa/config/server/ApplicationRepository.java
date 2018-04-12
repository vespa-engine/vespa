// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.io.Files;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.FileDistributionStatus;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.RefeedActions;
import com.yahoo.vespa.config.server.configchange.RestartActions;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.deploy.Deployment;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionFactory;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.Rotations;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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
    private final LogServerLogGrabber logServerLogGrabber;
    private final ApplicationConvergenceChecker convergeChecker;
    private final HttpProxy httpProxy;
    private final Clock clock;
    private final DeployLogger logger = new SilentDeployLogger();
    private final ConfigserverConfig configserverConfig;
    private final Environment environment;
    private final FileDistributionStatus fileDistributionStatus;

    @Inject
    public ApplicationRepository(Tenants tenants,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 LogServerLogGrabber logServerLogGrabber,
                                 ApplicationConvergenceChecker applicationConvergenceChecker,
                                 HttpProxy httpProxy, 
                                 ConfigserverConfig configserverConfig) {
        this(tenants, hostProvisionerProvider.getHostProvisioner(), logServerLogGrabber,
             applicationConvergenceChecker, httpProxy, configserverConfig, Clock.systemUTC(), new FileDistributionStatus());
    }

    // For testing
    public ApplicationRepository(Tenants tenants,
                                 Provisioner hostProvisioner,
                                 Clock clock) {
        this(tenants, Optional.of(hostProvisioner), new LogServerLogGrabber(),
             new ApplicationConvergenceChecker(), new HttpProxy(new SimpleHttpFetcher()),
             new ConfigserverConfig(new ConfigserverConfig.Builder()), clock, new FileDistributionStatus());
    }

    private ApplicationRepository(Tenants tenants,
                                  Optional<Provisioner> hostProvisioner,
                                  LogServerLogGrabber logServerLogGrabber,
                                  ApplicationConvergenceChecker applicationConvergenceChecker,
                                  HttpProxy httpProxy,
                                  ConfigserverConfig configserverConfig,
                                  Clock clock,
                                  FileDistributionStatus fileDistributionStatus) {
        this.tenants = tenants;
        this.hostProvisioner = hostProvisioner;
        this.logServerLogGrabber = logServerLogGrabber;
        this.convergeChecker = applicationConvergenceChecker;
        this.httpProxy = httpProxy;
        this.clock = clock;
        this.configserverConfig = configserverConfig;
        this.environment = Environment.from(configserverConfig.environment());
        this.fileDistributionStatus = fileDistributionStatus;
    }

    /**
     * Creates a new deployment from the active application, if available.
     *
     * @param application the active application to be redeployed
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    public Optional<com.yahoo.config.provision.Deployment> deployFromLocalActive(ApplicationId application) {
        return deployFromLocalActive(application, Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout()).plus(Duration.ofSeconds(5)));
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
        if (tenant == null) return Optional.empty();
        LocalSession activeSession = getActiveSession(tenant, application);
        if (activeSession == null) return Optional.empty();
        TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);
        LocalSession newSession = tenant.getSessionFactory().createSessionFromExisting(activeSession, logger, timeoutBudget);
        tenant.getLocalSessionRepo().addSession(newSession);

        // Keep manually deployed applications on the latest version, don't change version otherwise
        Version version = environment.isManuallyDeployed() ? Vtag.currentVersion : newSession.getVespaVersion();
                
        return Optional.of(Deployment.unprepared(newSession,
                                                 this,
                                                 hostProvisioner,
                                                 tenant,
                                                 timeout,
                                                 clock,
                                                 false, // don't validate as this is already deployed
                                                 version));
    }

    private Deployment deployFromPreparedSession(LocalSession session, Tenant tenant, Duration timeout) {
        return Deployment.prepared(session,
                                   this,
                                   hostProvisioner,
                                   tenant,
                                   timeout,
                                   clock);
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

    public HttpResponse serviceConvergenceCheck(Tenant tenant, ApplicationId applicationId, String hostname, URI uri) {
        Application application = getApplication(tenant, applicationId);
        return convergeChecker.serviceConvergenceCheck(application, hostname, uri);
    }

    public HttpResponse serviceListToCheckForConfigConvergence(Tenant tenant, ApplicationId applicationId, URI uri) {
        Application application = getApplication(tenant, applicationId);
        return convergeChecker.serviceListToCheckForConfigConvergence(application, uri);
    }

    public HttpResponse clusterControllerStatusPage(
            Tenant tenant,
            ApplicationId applicationId,
            String hostName,
            String pathSuffix) {
        Application application = getApplication(tenant, applicationId);

        // WARNING: pathSuffix may be given by the external user. Make sure no security issues arise...
        // We should be OK here, because at most, pathSuffix may change the parent path, but cannot otherwise
        // change the hostname and port. Exposing other paths on the cluster controller should be fine.
        // TODO: It would be nice to have a simple check to verify pathSuffix doesn't contain /../ components.
        String relativePath = "clustercontroller-status/" + pathSuffix;

        return httpProxy.get(application, hostName, "container-clustercontroller", relativePath);
    }

    public Long getApplicationGeneration(Tenant tenant, ApplicationId applicationId) {
        return getApplication(tenant, applicationId).getApplicationGeneration();
    }

    private Application getApplication(Tenant tenant, ApplicationId applicationId) {
        long sessionId = getSessionIdForApplication(tenant, applicationId);
        RemoteSession session = tenant.getRemoteSessionRepo().getSession(sessionId, 0);
        return session.ensureApplicationLoaded().getForVersionOrLatest(Optional.empty(), clock.instant());
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

    public HttpResponse filedistributionStatus(Tenant tenant, ApplicationId applicationId, Duration timeout) {
        Application application = getApplication(tenant, applicationId);
        return fileDistributionStatus.status(application, timeout);
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
        Deployment deployment = deployFromPreparedSession(localSession, tenant, timeoutBudget.timeLeft());
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

    public PrepareResult prepare(Tenant tenant, long sessionId, PrepareParams prepareParams, Instant now) {
        validateThatLocalSessionIsNotActive(tenant, sessionId);
        LocalSession session = getLocalSession(tenant, sessionId);
        ApplicationId applicationId = prepareParams.getApplicationId();
        Optional<ApplicationSet> currentActiveApplicationSet = getCurrentActiveApplicationSet(tenant, applicationId);
        Slime deployLog = createDeployLog();
        DeployLogger logger = new DeployHandlerLogger(deployLog.get().setArray("log"), prepareParams.isVerbose(), applicationId);
        ConfigChangeActions actions = session.prepare(logger, prepareParams, currentActiveApplicationSet, tenant.getPath(), now);
        logConfigChangeActions(actions, logger);
        log.log(LogLevel.INFO, Tenants.logPre(applicationId) + "Session " + sessionId + " prepared successfully. ");
        return new PrepareResult(sessionId, actions, deployLog);
    }

    public PrepareResult prepareAndActivate(Tenant tenant, long sessionId, PrepareParams prepareParams,
                                            boolean ignoreLockFailure, boolean ignoreSessionStaleFailure, Instant now) {
        PrepareResult result = prepare(tenant, sessionId, prepareParams, now);
        activate(tenant, sessionId, prepareParams.getTimeoutBudget(), ignoreLockFailure, ignoreSessionStaleFailure);
        return result;
    }

    public PrepareResult deploy(Tenant tenant, InputStream in, String contentType, PrepareParams prepareParams,
                                boolean ignoreLockFailure, boolean ignoreSessionStaleFailure, Instant now) {
        long sessionId = createSession(tenant, prepareParams.getTimeoutBudget(), in, contentType, prepareParams.getApplicationName());
        return prepareAndActivate(tenant, sessionId, prepareParams, ignoreLockFailure, ignoreSessionStaleFailure, now);
    }

    private File decompressApplication(InputStream in, String contentType, File tempDir) {
        try (CompressedApplicationInputStream application =
                     CompressedApplicationInputStream.createFromCompressedStream(in, contentType)) {
            return application.decompress(tempDir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to decompress data in body", e);
        }
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

    public long createSession(Tenant tenant, TimeoutBudget timeoutBudget, InputStream in, String contentType, String applicationName) {
        File tempDir = Files.createTempDir();
        long sessionId = createSession(tenant, timeoutBudget, decompressApplication(in, contentType, tempDir), applicationName);
        cleanupApplicationDirectory(tempDir, logger);
        return sessionId;
    }

    public long createSession(Tenant tenant, TimeoutBudget timeoutBudget, File applicationDirectory, String applicationName) {
        LocalSessionRepo localSessionRepo = tenant.getLocalSessionRepo();
        SessionFactory sessionFactory = tenant.getSessionFactory();
        LocalSession session = sessionFactory.createSession(applicationDirectory, applicationName, timeoutBudget);
        localSessionRepo.addSession(session);
        return session.getSessionId();
    }

    private static void cleanupApplicationDirectory(File tempDir, DeployLogger logger) {
        logger.log(LogLevel.DEBUG, "Deleting tmp dir '" + tempDir + "'");
        if (!IOUtils.recursiveDeleteDir(tempDir)) {
            logger.log(LogLevel.WARNING, "Not able to delete tmp dir '" + tempDir + "'");
        }
    }

    void redeployAllApplications() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(configserverConfig.numParallelTenantLoaders(),
                                                                new DaemonThreadFactory("redeploy apps"));
        // Keep track of deployment per application
        Map<ApplicationId, Future<?>> futures = new HashMap<>();
        tenants.getAllTenants()
                .forEach(tenant -> listApplicationIds(tenant)
                        .forEach(appId -> deployFromLocalActive(appId).ifPresent(
                                deployment -> futures.put(appId,executor.submit(deployment::activate)))));
        for (Map.Entry<ApplicationId, Future<?>> f : futures.entrySet()) {
            try {
                f.getValue().get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Redeploying of " + f.getKey() + " failed", e);
            }
        }
        executor.shutdown();
        executor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
    }

    public ApplicationFile getApplicationFileFromSession(TenantName tenantName, long sessionId, String path, LocalSession.Mode mode) {
        Tenant tenant = tenants.getTenant(tenantName);
        return getLocalSession(tenant, sessionId).getApplicationFile(Path.fromString(path), mode);
    }

    private LocalSession getExistingSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return getLocalSession(tenant, applicationRepo.getSessionIdForApplication(applicationId));
    }

    /**
     * Gets the active Session for the given application id.
     *
     * @return the active session, or null if there is no active session for the given application id.
     */
    public LocalSession getActiveSession(ApplicationId applicationId) {
        return getActiveSession(tenants.getTenant(applicationId.tenant()), applicationId);
    }

    private LocalSession getActiveSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        if (applicationRepo.listApplications().contains(applicationId)) {
            return tenant.getLocalSessionRepo().getSession(applicationRepo.getSessionIdForApplication(applicationId));
        }
        return null;
    }

    private static void logConfigChangeActions(ConfigChangeActions actions, DeployLogger logger) {
        RestartActions restartActions = actions.getRestartActions();
        if ( ! restartActions.isEmpty()) {
            logger.log(Level.WARNING, "Change(s) between active and new application that require restart:\n" +
                    restartActions.format());
        }
        RefeedActions refeedActions = actions.getRefeedActions();
        if ( ! refeedActions.isEmpty()) {
            boolean allAllowed = refeedActions.getEntries().stream().allMatch(RefeedActions.Entry::allowed);
            logger.log(allAllowed ? Level.INFO : Level.WARNING,
                       "Change(s) between active and new application that may require re-feed:\n" +
                               refeedActions.format());
        }
    }

    private Slime createDeployLog() {
        Slime deployLog = new Slime();
        deployLog.setObject();
        return deployLog;
    }

}
