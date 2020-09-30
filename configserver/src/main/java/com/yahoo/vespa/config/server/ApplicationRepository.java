// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.Metric;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.application.FileDistributionStatus;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.RefeedActions;
import com.yahoo.vespa.config.server.configchange.RestartActions;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.deploy.Deployment;
import com.yahoo.vespa.config.server.deploy.InfraDeployerProvider;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.LogRetriever;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;
import com.yahoo.vespa.config.server.http.TesterClient;
import com.yahoo.vespa.config.server.http.v2.DeploymentMetricsResponse;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.http.v2.ProtonMetricsResponse;
import com.yahoo.vespa.config.server.metrics.DeploymentMetricsRetriever;
import com.yahoo.vespa.config.server.metrics.ProtonMetricsRetriever;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.RemoteSession;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.ApplicationRolesStore;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantMetaData;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.curator.stats.ThreadLockStats;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.fileReferenceExistsOnDisk;
import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getFileReferencesOnDisk;
import static com.yahoo.vespa.config.server.tenant.TenantRepository.HOSTED_VESPA_TENANT;
import static com.yahoo.vespa.curator.Curator.CompletionWaiter;
import static com.yahoo.yolean.Exceptions.uncheck;
import static java.nio.file.Files.readAttributes;

/**
 * The API for managing applications.
 *
 * @author bratseth
 */
// TODO: Move logic for dealing with applications here from the HTTP layer and make this the persistent component
//       owning the rest of the state
public class ApplicationRepository implements com.yahoo.config.provision.Deployer {

    private static final Logger log = Logger.getLogger(ApplicationRepository.class.getName());

    private final AtomicBoolean bootstrapping = new AtomicBoolean(true);

    private final TenantRepository tenantRepository;
    private final Optional<Provisioner> hostProvisioner;
    private final Optional<InfraDeployer> infraDeployer;
    private final ConfigConvergenceChecker convergeChecker;
    private final HttpProxy httpProxy;
    private final Clock clock;
    private final DeployLogger logger = new SilentDeployLogger();
    private final ConfigserverConfig configserverConfig;
    private final FileDistributionStatus fileDistributionStatus = new FileDistributionStatus();
    private final Orchestrator orchestrator;
    private final LogRetriever logRetriever;
    private final TesterClient testerClient;
    private final Metric metric;
    private final BooleanFlag deployWithInternalRestart;

    @Inject
    public ApplicationRepository(TenantRepository tenantRepository,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 InfraDeployerProvider infraDeployerProvider,
                                 ConfigConvergenceChecker configConvergenceChecker,
                                 HttpProxy httpProxy,
                                 ConfigserverConfig configserverConfig,
                                 Orchestrator orchestrator,
                                 TesterClient testerClient,
                                 Metric metric,
                                 FlagSource flagSource) {
        this(tenantRepository,
             hostProvisionerProvider.getHostProvisioner(),
             infraDeployerProvider.getInfraDeployer(),
             configConvergenceChecker,
             httpProxy,
             configserverConfig,
             orchestrator,
             new LogRetriever(),
             Clock.systemUTC(),
             testerClient,
             metric,
             flagSource);
    }

    private ApplicationRepository(TenantRepository tenantRepository,
                                  Optional<Provisioner> hostProvisioner,
                                  Optional<InfraDeployer> infraDeployer,
                                  ConfigConvergenceChecker configConvergenceChecker,
                                  HttpProxy httpProxy,
                                  ConfigserverConfig configserverConfig,
                                  Orchestrator orchestrator,
                                  LogRetriever logRetriever,
                                  Clock clock,
                                  TesterClient testerClient,
                                  Metric metric,
                                  FlagSource flagSource) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.hostProvisioner = Objects.requireNonNull(hostProvisioner);
        this.infraDeployer = Objects.requireNonNull(infraDeployer);
        this.convergeChecker = Objects.requireNonNull(configConvergenceChecker);
        this.httpProxy = Objects.requireNonNull(httpProxy);
        this.configserverConfig = Objects.requireNonNull(configserverConfig);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.logRetriever = Objects.requireNonNull(logRetriever);
        this.clock = Objects.requireNonNull(clock);
        this.testerClient = Objects.requireNonNull(testerClient);
        this.metric = Objects.requireNonNull(metric);
        this.deployWithInternalRestart = Flags.DEPLOY_WITH_INTERNAL_RESTART.bindTo(flagSource);
    }

    public static class Builder {
        private TenantRepository tenantRepository;
        private Optional<Provisioner> hostProvisioner;
        private HttpProxy httpProxy = new HttpProxy(new SimpleHttpFetcher());
        private Clock clock = Clock.systemUTC();
        private ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder().build();
        private Orchestrator orchestrator;
        private LogRetriever logRetriever = new LogRetriever();
        private TesterClient testerClient = new TesterClient();
        private Metric metric = new NullMetric();
        private FlagSource flagSource = new InMemoryFlagSource();

        public Builder withTenantRepository(TenantRepository tenantRepository) {
            this.tenantRepository = tenantRepository;
            return this;
        }

        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder withProvisioner(Provisioner provisioner) {
            if (this.hostProvisioner != null) throw new IllegalArgumentException("provisioner already set in builder");
            this.hostProvisioner = Optional.ofNullable(provisioner);
            return this;
        }

        public Builder withHostProvisionerProvider(HostProvisionerProvider hostProvisionerProvider) {
            if (this.hostProvisioner != null) throw new IllegalArgumentException("provisioner already set in builder");
            this.hostProvisioner = hostProvisionerProvider.getHostProvisioner();
            return this;
        }

        public Builder withHttpProxy(HttpProxy httpProxy) {
            this.httpProxy = httpProxy;
            return this;
        }

        public Builder withConfigserverConfig(ConfigserverConfig configserverConfig) {
            this.configserverConfig = configserverConfig;
            return this;
        }

        public Builder withOrchestrator(Orchestrator orchestrator) {
            this.orchestrator = orchestrator;
            return this;
        }

        public Builder withLogRetriever(LogRetriever logRetriever) {
            this.logRetriever = logRetriever;
            return this;
        }

        public Builder withTesterClient(TesterClient testerClient) {
            this.testerClient = testerClient;
            return this;
        }

        public Builder withFlagSource(FlagSource flagSource) {
            this.flagSource = flagSource;
            return this;
        }

        public Builder withMetric(Metric metric) {
            this.metric = metric;
            return this;
        }

        public ApplicationRepository build() {
            return new ApplicationRepository(tenantRepository,
                                             hostProvisioner,
                                             InfraDeployerProvider.empty().getInfraDeployer(),
                                             new ConfigConvergenceChecker(),
                                             httpProxy,
                                             configserverConfig,
                                             orchestrator,
                                             logRetriever,
                                             clock,
                                             testerClient,
                                             metric,
                                             flagSource);
        }

    }

    public Metric metric() {
        return metric;
    }

    // ---------------- Deploying ----------------------------------------------------------------

    @Override
    public boolean bootstrapping() {
        return bootstrapping.get();
    }

    public void bootstrappingDone() {
        bootstrapping.set(false);
    }

    public PrepareResult prepare(Tenant tenant, long sessionId, PrepareParams prepareParams) {
        DeployHandlerLogger logger = DeployHandlerLogger.forPrepareParams(prepareParams);
        Deployment deployment = prepare(tenant, sessionId, prepareParams, logger);
        return new PrepareResult(sessionId, deployment.configChangeActions(), logger);
    }

    private Deployment prepare(Tenant tenant, long sessionId, PrepareParams prepareParams, DeployHandlerLogger logger) {
        validateThatLocalSessionIsNotActive(tenant, sessionId);
        LocalSession session = getLocalSession(tenant, sessionId);
        ApplicationId applicationId = prepareParams.getApplicationId();
        Deployment deployment = Deployment.unprepared(session, this, hostProvisioner, tenant, prepareParams, logger, clock);
        deployment.prepare();

        logConfigChangeActions(deployment.configChangeActions(), logger);
        log.log(Level.INFO, TenantRepository.logPre(applicationId) + "Session " + sessionId + " prepared successfully. ");
        return deployment;
    }

    public PrepareResult deploy(CompressedApplicationInputStream in, PrepareParams prepareParams) {
        File tempDir = uncheck(() -> Files.createTempDirectory("deploy")).toFile();
        ThreadLockStats threadLockStats = LockStats.getForCurrentThread();
        PrepareResult prepareResult;
        try {
            threadLockStats.startRecording("deploy of " + prepareParams.getApplicationId().serializedForm());
            prepareResult = deploy(decompressApplication(in, tempDir), prepareParams);
        } finally {
            threadLockStats.stopRecording();
            cleanupTempDirectory(tempDir);
        }
        return prepareResult;
    }

    public PrepareResult deploy(File applicationPackage, PrepareParams prepareParams) {
        ApplicationId applicationId = prepareParams.getApplicationId();
        long sessionId = createSession(applicationId, prepareParams.getTimeoutBudget(), applicationPackage);
        Tenant tenant = getTenant(applicationId);
        DeployHandlerLogger logger = DeployHandlerLogger.forPrepareParams(prepareParams);
        Deployment deployment = prepare(tenant, sessionId, prepareParams, logger);
        deployment.activate();

        return new PrepareResult(sessionId, deployment.configChangeActions(), logger);
    }

    /**
     * Creates a new deployment from the active application, if available.
     * This is used for system internal redeployments, not on application package changes.
     *
     * @param application the active application to be redeployed
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    @Override
    public Optional<com.yahoo.config.provision.Deployment> deployFromLocalActive(ApplicationId application) {
        return deployFromLocalActive(application, false);
    }

    /**
     * Creates a new deployment from the active application, if available.
     * This is used for system internal redeployments, not on application package changes.
     *
     * @param application the active application to be redeployed
     * @param bootstrap the deployment is done when bootstrapping
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    @Override
    public Optional<com.yahoo.config.provision.Deployment> deployFromLocalActive(ApplicationId application,
                                                                                 boolean bootstrap) {
        return deployFromLocalActive(application,
                                     Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout()).plus(Duration.ofSeconds(5)),
                                     bootstrap);
    }

    /**
     * Creates a new deployment from the active application, if available.
     * This is used for system internal redeployments, not on application package changes.
     *
     * @param application the active application to be redeployed
     * @param timeout the timeout to use for each individual deployment operation
     * @param bootstrap the deployment is done when bootstrapping
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    @Override
    public Optional<com.yahoo.config.provision.Deployment> deployFromLocalActive(ApplicationId application,
                                                                                 Duration timeout,
                                                                                 boolean bootstrap) {
        Optional<com.yahoo.config.provision.Deployment> infraDeployment = infraDeployer.flatMap(d -> d.getDeployment(application));
        if (infraDeployment.isPresent()) return infraDeployment;

        Tenant tenant = tenantRepository.getTenant(application.tenant());
        if (tenant == null) return Optional.empty();
        LocalSession activeSession = getActiveLocalSession(tenant, application);
        if (activeSession == null) return Optional.empty();
        TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);
        SessionRepository sessionRepository = tenant.getSessionRepository();
        LocalSession newSession = sessionRepository.createSessionFromExisting(activeSession, logger, true, timeoutBudget);
        sessionRepository.addLocalSession(newSession);
        boolean internalRestart = deployWithInternalRestart.with(FetchVector.Dimension.APPLICATION_ID, application.serializedForm()).value();

        return Optional.of(Deployment.unprepared(newSession, this, hostProvisioner, tenant, logger, timeout, clock,
                                                 false /* don't validate as this is already deployed */, bootstrap, internalRestart));
    }

    @Override
    public Optional<Instant> lastDeployTime(ApplicationId application) {
        Tenant tenant = tenantRepository.getTenant(application.tenant());
        if (tenant == null) return Optional.empty();
        RemoteSession activeSession = getActiveSession(tenant, application);
        if (activeSession == null) return Optional.empty();
        return Optional.of(activeSession.getCreateTime());
    }

    public ApplicationId activate(Tenant tenant,
                                  long sessionId,
                                  TimeoutBudget timeoutBudget,
                                  boolean force) {
        LocalSession localSession = getLocalSession(tenant, sessionId);
        Deployment deployment = Deployment.prepared(localSession, this, hostProvisioner, tenant, logger, timeoutBudget.timeout(), clock, false, force);
        deployment.activate();
        return localSession.getApplicationId();
    }

    public Transaction deactivateCurrentActivateNew(Session active, LocalSession prepared, boolean force) {
        Tenant tenant = tenantRepository.getTenant(prepared.getTenantName());
        Transaction transaction = tenant.getSessionRepository().createActivateTransaction(prepared);
        if (active != null) {
            checkIfActiveHasChanged(prepared, active, force);
            checkIfActiveIsNewerThanSessionToBeActivated(prepared.getSessionId(), active.getSessionId());
            transaction.add(active.createDeactivateTransaction().operations());
        }
        transaction.add(updateMetaDataWithDeployTimestamp(tenant, clock.instant()));
        return transaction;
    }

    private List<Transaction.Operation> updateMetaDataWithDeployTimestamp(Tenant tenant, Instant deployTimestamp) {
        TenantMetaData tenantMetaData = getTenantMetaData(tenant).withLastDeployTimestamp(deployTimestamp);
        return tenantRepository.createWriteTenantMetaDataTransaction(tenantMetaData).operations();
    }

    TenantMetaData getTenantMetaData(Tenant tenant) {
        return tenantRepository.getTenantMetaData(tenant);
    }

    static void checkIfActiveHasChanged(LocalSession session, Session currentActiveSession, boolean ignoreStaleSessionFailure) {
        long activeSessionAtCreate = session.getActiveSessionAtCreate();
        log.log(Level.FINE, currentActiveSession.logPre() + "active session id at create time=" + activeSessionAtCreate);
        if (activeSessionAtCreate == 0) return; // No active session at create

        long sessionId = session.getSessionId();
        long currentActiveSessionSessionId = currentActiveSession.getSessionId();
        log.log(Level.FINE, currentActiveSession.logPre() + "sessionId=" + sessionId +
                            ", current active session=" + currentActiveSessionSessionId);
        if (currentActiveSession.isNewerThan(activeSessionAtCreate) &&
            currentActiveSessionSessionId != sessionId) {
            String errMsg = currentActiveSession.logPre() + "Cannot activate session " +
                            sessionId + " because the currently active session (" +
                            currentActiveSessionSessionId + ") has changed since session " + sessionId +
                            " was created (was " + activeSessionAtCreate + " at creation time)";
            if (ignoreStaleSessionFailure) {
                log.warning(errMsg + " (Continuing because of force.)");
            } else {
                throw new ActivationConflictException(errMsg);
            }
        }
    }

    // As of now, config generation is based on session id, and config generation must be a monotonically
    // increasing number
    static void checkIfActiveIsNewerThanSessionToBeActivated(long sessionId, long currentActiveSessionId) {
        if (sessionId < currentActiveSessionId) {
            throw new ActivationConflictException("It is not possible to activate session " + sessionId +
                                                  ", because it is older than current active session (" +
                                                  currentActiveSessionId + ")");
        }
    }

    // ---------------- Application operations ----------------------------------------------------------------

    /**
     * Deletes an application
     *
     * @return true if the application was found and deleted, false if it was not present
     * @throws RuntimeException if the delete transaction fails. This method is exception safe.
     */
    public boolean delete(ApplicationId applicationId) {
        Tenant tenant = getTenant(applicationId);
        if (tenant == null) return false;

        TenantApplications tenantApplications = tenant.getApplicationRepo();
        try (Lock lock = tenantApplications.lock(applicationId)) {
            Optional<Long> activeSession = tenantApplications.activeSessionOf(applicationId);
            if (activeSession.isEmpty()) return false;

            // Deleting an application is done by deleting the remote session, other config
            // servers will pick this up and clean up through the watcher in this class
            try {
                RemoteSession remoteSession = getRemoteSession(tenant, activeSession.get());
                tenant.getSessionRepository().delete(remoteSession);
            } catch (NotFoundException e) {
                log.log(Level.INFO, TenantRepository.logPre(applicationId) + "Active session exists, but has not been deleted properly. Trying to cleanup");
            }

            NestedTransaction transaction = new NestedTransaction();
            Curator curator = tenantRepository.getCurator();
            transaction.add(new ContainerEndpointsCache(tenant.getPath(), curator).delete(applicationId)); // TODO: Not unit tested
            // Delete any application roles
            transaction.add(new ApplicationRolesStore(curator, tenant.getPath()).delete(applicationId));
            // Delete endpoint certificates
            transaction.add(new EndpointCertificateMetadataStore(curator, tenant.getPath()).delete(applicationId));
            // This call will remove application in zookeeper. Watches in TenantApplications will remove the application
            // and allocated hosts in model and handlers in RPC server
            transaction.add(tenantApplications.createDeleteTransaction(applicationId));

            hostProvisioner.ifPresent(provisioner -> provisioner.remove(transaction, applicationId));
            transaction.onCommitted(() -> log.log(Level.INFO, "Deleted " + applicationId));
            transaction.commit();
            return true;
        }
    }

    public HttpResponse clusterControllerStatusPage(ApplicationId applicationId, String hostName, String pathSuffix) {
        // WARNING: pathSuffix may be given by the external user. Make sure no security issues arise...
        // We should be OK here, because at most, pathSuffix may change the parent path, but cannot otherwise
        // change the hostname and port. Exposing other paths on the cluster controller should be fine.
        // TODO: It would be nice to have a simple check to verify pathSuffix doesn't contain /../ components.
        String relativePath = "clustercontroller-status/" + pathSuffix;

        return httpProxy.get(getApplication(applicationId), hostName,
                             CLUSTERCONTROLLER_CONTAINER.serviceName, relativePath);
    }

    public Long getApplicationGeneration(ApplicationId applicationId) {
        return getApplication(applicationId).getApplicationGeneration();
    }

    public void restart(ApplicationId applicationId, HostFilter hostFilter) {
        hostProvisioner.ifPresent(provisioner -> provisioner.restart(applicationId, hostFilter));
    }

    public boolean isSuspended(ApplicationId application) {
        return orchestrator.getAllSuspendedApplications().contains(application);
    }

    public HttpResponse filedistributionStatus(ApplicationId applicationId, Duration timeout) {
        return fileDistributionStatus.status(getApplication(applicationId), timeout);
    }

    public Set<String> deleteUnusedFiledistributionReferences(File fileReferencesPath, Duration keepFileReferences) {
        if (!fileReferencesPath.isDirectory()) throw new RuntimeException(fileReferencesPath + " is not a directory");

        Set<String> fileReferencesInUse = new HashSet<>();
        // Intentionally skip applications that we for some reason do not find
        // or that we fail to get file references for (they will be retried on the next run)
        for (var applicationId : listApplications()) {
            try {
                Optional<Application> app = getOptionalApplication(applicationId);
                if (app.isEmpty()) continue;
                fileReferencesInUse.addAll(app.get().getModel().fileReferences().stream()
                                                   .map(FileReference::value)
                                                   .collect(Collectors.toSet()));
            } catch (Exception e) {
                log.log(Level.WARNING, "Getting file references in use for '" + applicationId + "' failed", e);
            }
        }
        log.log(Level.FINE, "File references in use : " + fileReferencesInUse);

        // Find those on disk that are not in use
        Set<String> fileReferencesOnDisk = getFileReferencesOnDisk(fileReferencesPath);
        log.log(Level.FINE, "File references on disk (in " + fileReferencesPath + "): " + fileReferencesOnDisk);

        Instant instant = Instant.now().minus(keepFileReferences);
        Set<String> fileReferencesToDelete = fileReferencesOnDisk
                .stream()
                .filter(fileReference -> ! fileReferencesInUse.contains(fileReference))
                .filter(fileReference -> isFileLastModifiedBefore(new File(fileReferencesPath, fileReference), instant))
                .collect(Collectors.toSet());
        if (fileReferencesToDelete.size() > 0) {
            log.log(Level.FINE, "Will delete file references not in use: " + fileReferencesToDelete);
            fileReferencesToDelete.forEach(fileReference -> {
                File file = new File(fileReferencesPath, fileReference);
                if ( ! IOUtils.recursiveDeleteDir(file))
                    log.log(Level.WARNING, "Could not delete " + file.getAbsolutePath());
            });
        }
        return fileReferencesToDelete;
    }

    public Set<FileReference> getFileReferences(ApplicationId applicationId) {
        return getOptionalApplication(applicationId).map(app -> app.getModel().fileReferences()).orElse(Set.of());
    }

    public ApplicationFile getApplicationFileFromSession(TenantName tenantName, long sessionId, String path, LocalSession.Mode mode) {
        Tenant tenant = tenantRepository.getTenant(tenantName);
        return getLocalSession(tenant, sessionId).getApplicationFile(Path.fromString(path), mode);
    }

    public Tenant getTenant(ApplicationId applicationId) {
        return tenantRepository.getTenant(applicationId.tenant());
    }

    private Application getApplication(ApplicationId applicationId) {
        return getApplication(applicationId, Optional.empty());
    }

    private Application getApplication(ApplicationId applicationId, Optional<Version> version) {
        try {
            Tenant tenant = getTenant(applicationId);
            if (tenant == null) throw new NotFoundException("Tenant '" + applicationId.tenant() + "' not found");
            long sessionId = getSessionIdForApplication(tenant, applicationId);
            RemoteSession session = getRemoteSession(tenant, sessionId);
            return session.ensureApplicationLoaded().getForVersionOrLatest(version, clock.instant());
        } catch (NotFoundException e) {
            log.log(Level.WARNING, "Failed getting application for '" + applicationId + "': " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed getting application for '" + applicationId + "'", e);
            throw e;
        }
    }

    // Will return Optional.empty() if getting application fails (instead of throwing an exception)
    private Optional<Application> getOptionalApplication(ApplicationId applicationId) {
        try {
            return Optional.of(getApplication(applicationId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Set<ApplicationId> listApplications() {
        return tenantRepository.getAllTenants().stream()
                .flatMap(tenant -> tenant.getApplicationRepo().activeApplications().stream())
                .collect(Collectors.toSet());
    }

    private boolean isFileLastModifiedBefore(File fileReference, Instant instant) {
        BasicFileAttributes fileAttributes;
        try {
            fileAttributes = readAttributes(fileReference.toPath(), BasicFileAttributes.class);
            return fileAttributes.lastModifiedTime().toInstant().isBefore(instant);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<String> getApplicationPackageReference(ApplicationId applicationId) {
        Optional<String> applicationPackage = Optional.empty();
        RemoteSession session = getActiveSession(applicationId);
        if (session != null) {
            FileReference applicationPackageReference = session.getApplicationPackageReference();
            File downloadDirectory = new File(Defaults.getDefaults().underVespaHome(configserverConfig().fileReferencesDir()));
            if (applicationPackageReference != null && ! fileReferenceExistsOnDisk(downloadDirectory, applicationPackageReference))
                applicationPackage = Optional.of(applicationPackageReference.value());
        }
        return applicationPackage;
    }

    public List<Version> getAllVersions(ApplicationId applicationId) {
        Optional<ApplicationSet> applicationSet = getCurrentActiveApplicationSet(getTenant(applicationId), applicationId);
        if (applicationSet.isEmpty())
            return List.of();
        else
            return applicationSet.get().getAllVersions(applicationId);
    }

    // ---------------- Convergence ----------------------------------------------------------------

    public HttpResponse checkServiceForConfigConvergence(ApplicationId applicationId, String hostAndPort, URI uri,
                                                         Duration timeout, Optional<Version> vespaVersion) {
        return convergeChecker.checkService(getApplication(applicationId, vespaVersion), hostAndPort, uri, timeout);
    }

    public HttpResponse servicesToCheckForConfigConvergence(ApplicationId applicationId, URI uri,
                                                            Duration timeoutPerService, Optional<Version> vespaVersion) {
        return convergeChecker.servicesToCheck(getApplication(applicationId, vespaVersion), uri, timeoutPerService);
    }

    // ---------------- Logs ----------------------------------------------------------------

    public HttpResponse getLogs(ApplicationId applicationId, Optional<String> hostname, String apiParams) {
        String logServerURI = getLogServerURI(applicationId, hostname) + apiParams;
        return logRetriever.getLogs(logServerURI);
    }

    // ---------------- Methods to do call against tester containers in hosted ------------------------------

    public HttpResponse getTesterStatus(ApplicationId applicationId) {
        return testerClient.getStatus(getTesterHostname(applicationId), getTesterPort(applicationId));
    }

    public HttpResponse getTesterLog(ApplicationId applicationId, Long after) {
        return testerClient.getLog(getTesterHostname(applicationId), getTesterPort(applicationId), after);
    }

    public HttpResponse startTests(ApplicationId applicationId, String suite, byte[] config) {
        return testerClient.startTests(getTesterHostname(applicationId), getTesterPort(applicationId), suite, config);
    }

    public HttpResponse isTesterReady(ApplicationId applicationId) {
        return testerClient.isTesterReady(getTesterHostname(applicationId), getTesterPort(applicationId));
    }

    public HttpResponse getTestReport(ApplicationId applicationId) {
        return testerClient.getReport(getTesterHostname(applicationId), getTesterPort(applicationId));
    }

    private String getTesterHostname(ApplicationId applicationId) {
        return getTesterServiceInfo(applicationId).getHostName();
    }

    private int getTesterPort(ApplicationId applicationId) {
        ServiceInfo serviceInfo = getTesterServiceInfo(applicationId);
        return serviceInfo.getPorts().stream().filter(portInfo -> portInfo.getTags().contains("http")).findFirst().get().getPort();
    }

    private ServiceInfo getTesterServiceInfo(ApplicationId applicationId) {
        Application application = getApplication(applicationId);
        return application.getModel().getHosts().stream()
                .findFirst().orElseThrow(() -> new InternalServerException("Could not find any host for tester app " + applicationId.toFullString()))
                .getServices().stream()
                .filter(service -> CONTAINER.serviceName.equals(service.getServiceType()))
                .findFirst()
                .orElseThrow(() -> new InternalServerException("Could not find any tester container for tester app " + applicationId.toFullString()));
    }

    // ---------------- Session operations ----------------------------------------------------------------



    public CompletionWaiter activate(LocalSession session, Session previousActiveSession, ApplicationId applicationId, boolean force) {
        CompletionWaiter waiter = session.getSessionZooKeeperClient().createActiveWaiter();
        NestedTransaction transaction = new NestedTransaction();
        transaction.add(deactivateCurrentActivateNew(previousActiveSession, session, force));
        hostProvisioner.ifPresent(provisioner -> provisioner.activate(transaction, applicationId, session.getAllocatedHosts().getHosts()));
        transaction.commit();
        return waiter;
    }

    /**
     * Gets the active Session for the given application id.
     *
     * @return the active session, or null if there is no active session for the given application id.
     */
    public RemoteSession getActiveSession(ApplicationId applicationId) {
        Tenant tenant = getTenant(applicationId);
        if (tenant == null) throw new IllegalArgumentException("Could not find any tenant for '" + applicationId + "'");
        return getActiveSession(tenant, applicationId);
    }

    public long getSessionIdForApplication(ApplicationId applicationId) {
        Tenant tenant = getTenant(applicationId);
        if (tenant == null) throw new NotFoundException("Tenant '" + applicationId.tenant() + "' not found");
        return getSessionIdForApplication(tenant, applicationId);
    }

    private long getSessionIdForApplication(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        if (! applicationRepo.exists(applicationId))
            throw new NotFoundException("Unknown application id '" + applicationId + "'");
        return applicationRepo.requireActiveSessionOf(applicationId);
    }

    public void validateThatSessionIsNotActive(Tenant tenant, long sessionId) {
        Session session = getRemoteSession(tenant, sessionId);
        if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalStateException("Session is active: " + sessionId);
        }
    }

    public void validateThatSessionIsPrepared(Tenant tenant, long sessionId) {
        Session session = getRemoteSession(tenant, sessionId);
        if ( ! Session.Status.PREPARE.equals(session.getStatus()))
            throw new IllegalStateException("Session not prepared: " + sessionId);
    }

    public long createSessionFromExisting(ApplicationId applicationId,
                                          DeployLogger logger,
                                          boolean internalRedeploy,
                                          TimeoutBudget timeoutBudget) {
        Tenant tenant = getTenant(applicationId);
        SessionRepository sessionRepository = tenant.getSessionRepository();
        RemoteSession fromSession = getExistingSession(tenant, applicationId);
        LocalSession session = sessionRepository.createSessionFromExisting(fromSession, logger, internalRedeploy, timeoutBudget);
        sessionRepository.addLocalSession(session);
        return session.getSessionId();
    }

    public long createSession(ApplicationId applicationId, TimeoutBudget timeoutBudget, InputStream in, String contentType) {
        File tempDir = uncheck(() -> Files.createTempDirectory("deploy")).toFile();
        long sessionId;
        try {
            sessionId = createSession(applicationId, timeoutBudget, decompressApplication(in, contentType, tempDir));
        } finally {
            cleanupTempDirectory(tempDir);
        }
        return sessionId;
    }

    public long createSession(ApplicationId applicationId, TimeoutBudget timeoutBudget, File applicationDirectory) {
        Tenant tenant = getTenant(applicationId);
        tenant.getApplicationRepo().createApplication(applicationId);
        Optional<Long> activeSessionId = tenant.getApplicationRepo().activeSessionOf(applicationId);
        LocalSession session = tenant.getSessionRepository().createSession(applicationDirectory,
                                                                           applicationId,
                                                                           timeoutBudget,
                                                                           activeSessionId);
        tenant.getSessionRepository().addLocalSession(session);
        return session.getSessionId();
    }

    public void deleteExpiredLocalSessions() {
        Map<Tenant, Collection<LocalSession>> sessionsPerTenant = new HashMap<>();
        tenantRepository.getAllTenants().forEach(tenant -> sessionsPerTenant.put(tenant, tenant.getSessionRepository().getLocalSessions()));

        Set<ApplicationId> applicationIds = new HashSet<>();
        sessionsPerTenant.values()
                .forEach(sessionList -> sessionList.stream()
                        .map(Session::getOptionalApplicationId)
                        .filter(Optional::isPresent)
                        .forEach(appId -> applicationIds.add(appId.get())));

        Map<ApplicationId, Long> activeSessions = new HashMap<>();
        applicationIds.forEach(applicationId -> {
            RemoteSession activeSession = getActiveSession(applicationId);
            if (activeSession != null)
                activeSessions.put(applicationId, activeSession.getSessionId());
        });
        sessionsPerTenant.keySet().forEach(tenant -> tenant.getSessionRepository().deleteExpiredSessions(activeSessions));
    }

    public int deleteExpiredSessionLocks(Duration expiryTime) {
        return tenantRepository.getAllTenants()
                .stream()
                .map(tenant -> tenant.getSessionRepository().deleteExpiredLocks(clock, expiryTime))
                .mapToInt(i -> i)
                .sum();
    }

    public int deleteExpiredRemoteSessions(Duration expiryTime) {
        return deleteExpiredRemoteSessions(clock, expiryTime);
    }

    public int deleteExpiredRemoteSessions(Clock clock, Duration expiryTime) {
        return tenantRepository.getAllTenants()
                .stream()
                .map(tenant -> tenant.getSessionRepository().deleteExpiredRemoteSessions(clock, expiryTime))
                .mapToInt(i -> i)
                .sum();
    }

    // ---------------- Tenant operations ----------------------------------------------------------------


    public TenantRepository tenantRepository() {
        return tenantRepository;
    }

    public Set<TenantName> deleteUnusedTenants(Duration ttlForUnusedTenant, Instant now) {
        return tenantRepository.getAllTenantNames().stream()
                .filter(tenantName -> activeApplications(tenantName).isEmpty())
                .filter(tenantName -> !tenantName.equals(TenantName.defaultName())) // Not allowed to remove 'default' tenant
                .filter(tenantName -> !tenantName.equals(HOSTED_VESPA_TENANT)) // Not allowed to remove 'hosted-vespa' tenant
                .filter(tenantName -> getTenantMetaData(tenantRepository.getTenant(tenantName)).lastDeployTimestamp().isBefore(now.minus(ttlForUnusedTenant)))
                .peek(tenantRepository::deleteTenant)
                .collect(Collectors.toSet());
    }

    public void deleteTenant(TenantName tenantName) {
        List<ApplicationId> activeApplications = activeApplications(tenantName);
        if (activeApplications.isEmpty())
            tenantRepository.deleteTenant(tenantName);
        else
            throw new IllegalArgumentException("Cannot delete tenant '" + tenantName + "', it has active applications: " + activeApplications);
    }

    private List<ApplicationId> activeApplications(TenantName tenantName) {
        return tenantRepository.getTenant(tenantName).getApplicationRepo().activeApplications();
    }
    // ---------------- Proton Metrics V1 ------------------------------------------------------------------------

    public ProtonMetricsResponse getProtonMetrics(ApplicationId applicationId) {
        Application application = getApplication(applicationId);
        ProtonMetricsRetriever protonMetricsRetriever = new ProtonMetricsRetriever();
        return protonMetricsRetriever.getMetrics(application);
    }


    // ---------------- Deployment Metrics V1 ------------------------------------------------------------------------

    public DeploymentMetricsResponse getDeploymentMetrics(ApplicationId applicationId) {
        Application application = getApplication(applicationId);
        DeploymentMetricsRetriever deploymentMetricsRetriever = new DeploymentMetricsRetriever();
        return deploymentMetricsRetriever.getMetrics(application);
    }

    // ---------------- Misc operations ----------------------------------------------------------------

    public ApplicationMetaData getMetadataFromLocalSession(Tenant tenant, long sessionId) {
        return getLocalSession(tenant, sessionId).getMetaData();
    }

    public ConfigserverConfig configserverConfig() {
        return configserverConfig;
    }

    public ApplicationId getApplicationIdForHostname(String hostname) {
        Optional<ApplicationId> applicationId = tenantRepository.getAllTenantNames().stream()
                .map(tenantName -> tenantRepository.getTenant(tenantName).getApplicationRepo().getApplicationIdForHostName(hostname))
                .filter(Objects::nonNull)
                .findFirst();
        return applicationId.orElse(null);
    }

    private void validateThatLocalSessionIsNotActive(Tenant tenant, long sessionId) {
        LocalSession session = getLocalSession(tenant, sessionId);
        if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalStateException("Session is active: " + sessionId);
        }
    }

    private LocalSession getLocalSession(Tenant tenant, long sessionId) {
        LocalSession session = tenant.getSessionRepository().getLocalSession(sessionId);
        if (session == null) throw new NotFoundException("Session " + sessionId + " was not found");

        return session;
    }

    private RemoteSession getRemoteSession(Tenant tenant, long sessionId) {
        RemoteSession session = tenant.getSessionRepository().getRemoteSession(sessionId);
        if (session == null) throw new NotFoundException("Session " + sessionId + " was not found");

        return session;
    }

    public Optional<ApplicationSet> getCurrentActiveApplicationSet(Tenant tenant, ApplicationId appId) {
        Optional<ApplicationSet> currentActiveApplicationSet = Optional.empty();
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        try {
            long currentActiveSessionId = applicationRepo.requireActiveSessionOf(appId);
            RemoteSession currentActiveSession = getRemoteSession(tenant, currentActiveSessionId);
            currentActiveApplicationSet = Optional.ofNullable(currentActiveSession.ensureApplicationLoaded());
        } catch (IllegalArgumentException e) {
            // Do nothing if we have no currently active session
        }
        return currentActiveApplicationSet;
    }

    private File decompressApplication(InputStream in, String contentType, File tempDir) {
        try (CompressedApplicationInputStream application =
                     CompressedApplicationInputStream.createFromCompressedStream(in, contentType)) {
            return decompressApplication(application, tempDir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to decompress data in body", e);
        }
    }

    private File decompressApplication(CompressedApplicationInputStream in, File tempDir) {
        try {
            return in.decompress(tempDir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to decompress stream", e);
        }
    }

    private void cleanupTempDirectory(File tempDir) {
        logger.log(Level.FINE, "Deleting tmp dir '" + tempDir + "'");
        if (!IOUtils.recursiveDeleteDir(tempDir)) {
            logger.log(Level.WARNING, "Not able to delete tmp dir '" + tempDir + "'");
        }
    }

    private RemoteSession getExistingSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return getRemoteSession(tenant, applicationRepo.requireActiveSessionOf(applicationId));
    }

    private RemoteSession getActiveSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        if (applicationRepo.activeApplications().contains(applicationId)) {
            return tenant.getSessionRepository().getRemoteSession(applicationRepo.requireActiveSessionOf(applicationId));
        }
        return null;
    }

    public LocalSession getActiveLocalSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        if (applicationRepo.activeApplications().contains(applicationId)) {
            return tenant.getSessionRepository().getLocalSession(applicationRepo.requireActiveSessionOf(applicationId));
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

    private String getLogServerURI(ApplicationId applicationId, Optional<String> hostname) {
        // Allow to get logs from a given hostname if the application is under the hosted-vespa tenant.
        // We make no validation that the hostname is actually allocated to the given application since
        // most applications under hosted-vespa are not known to the model and it's OK for a user to get
        // logs for any host if they are authorized for the hosted-vespa tenant.
        if (hostname.isPresent() && HOSTED_VESPA_TENANT.equals(applicationId.tenant())) {
            return "http://" + hostname.get() + ":8080/logs";
        }

        Application application = getApplication(applicationId);
        Collection<HostInfo> hostInfos = application.getModel().getHosts();

        HostInfo logServerHostInfo = hostInfos.stream()
                .filter(host -> host.getServices().stream()
                        .anyMatch(serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Could not find host info for logserver"));

        ServiceInfo serviceInfo = logServerHostInfo.getServices().stream().filter(service -> List.of(LOGSERVER_CONTAINER.serviceName, CONTAINER.serviceName).contains(service.getServiceType()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No container running on logserver host"));
        int port = servicePort(serviceInfo);
        return "http://" + logServerHostInfo.getHostname() + ":" + port + "/logs";
    }

    private int servicePort(ServiceInfo serviceInfo) {
        return serviceInfo.getPorts().stream()
                .filter(portInfo -> portInfo.getTags().stream().anyMatch(tag -> tag.equalsIgnoreCase("http")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Could not find HTTP port"))
                .getPort();
    }

    public Zone zone() {
        return new Zone(SystemName.from(configserverConfig.system()),
                        Environment.from(configserverConfig.environment()),
                        RegionName.from(configserverConfig.region()));
    }

    /** Emits as a metric the time in millis spent while holding this timer, with deployment ID as dimensions. */
    public ActionTimer timerFor(ApplicationId id, String metricName) {
        return new ActionTimer(metric, clock, id, configserverConfig.environment(), configserverConfig.region(), metricName);
    }

    public static class ActionTimer implements AutoCloseable {

        private final Metric metric;
        private final Clock clock;
        private final ApplicationId id;
        private final String environment;
        private final String region;
        private final String name;
        private final Instant start;

        private ActionTimer(Metric metric, Clock clock, ApplicationId id, String environment, String region, String name) {
            this.metric = metric;
            this.clock = clock;
            this.id = id;
            this.environment = environment;
            this.region = region;
            this.name = name;
            this.start = clock.instant();
        }

        @Override
        public void close() {
            metric.set(name,
                       Duration.between(start, clock.instant()).toMillis(),
                       metric.createContext(Map.of("applicationId", id.toFullString(),
                                                   "tenantName", id.tenant().value(),
                                                   "app", id.application().value() + "." + id.instance().value(),
                                                   "zone", environment + "." + region)));
        }

    }

}
