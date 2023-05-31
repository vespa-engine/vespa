// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Query;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.EndpointsChecker;
import com.yahoo.config.provision.EndpointsChecker.Availability;
import com.yahoo.config.provision.EndpointsChecker.Endpoint;
import com.yahoo.config.provision.EndpointsChecker.HealthCheckerProvider;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.exception.ActivationConflictException;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.SecretStoreProvider;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.Metric;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.ClusterReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexingStatusClient;
import com.yahoo.vespa.config.server.application.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.application.DefaultClusterReindexingStatusClient;
import com.yahoo.vespa.config.server.application.FileDistributionStatus;
import com.yahoo.vespa.config.server.application.HttpProxy;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.RefeedActions;
import com.yahoo.vespa.config.server.configchange.ReindexActions;
import com.yahoo.vespa.config.server.configchange.RestartActions;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.deploy.Deployment;
import com.yahoo.vespa.config.server.deploy.InfraDeployerProvider;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.LogRetriever;
import com.yahoo.vespa.config.server.http.SecretStoreValidator;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;
import com.yahoo.vespa.config.server.http.TesterClient;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.http.v2.response.DeploymentMetricsResponse;
import com.yahoo.vespa.config.server.http.v2.response.SearchNodeMetricsResponse;
import com.yahoo.vespa.config.server.metrics.DeploymentMetricsRetriever;
import com.yahoo.vespa.config.server.metrics.SearchNodeMetricsRetriever;
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
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.curator.stats.ThreadLockStats;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceResponse;
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
    private final EndpointsChecker endpointsChecker;
    private final Clock clock;
    private final ConfigserverConfig configserverConfig;
    private final FileDistributionStatus fileDistributionStatus = new FileDistributionStatus();
    private final Orchestrator orchestrator;
    private final LogRetriever logRetriever;
    private final TesterClient testerClient;
    private final Metric metric;
    private final SecretStoreValidator secretStoreValidator;
    private final ClusterReindexingStatusClient clusterReindexingStatusClient;
    private final FlagSource flagSource;

    @Inject
    public ApplicationRepository(TenantRepository tenantRepository,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 InfraDeployerProvider infraDeployerProvider,
                                 ConfigConvergenceChecker configConvergenceChecker,
                                 HttpProxy httpProxy,
                                 ConfigserverConfig configserverConfig,
                                 Orchestrator orchestrator,
                                 TesterClient testerClient,
                                 Zone zone,
                                 HealthCheckerProvider healthCheckers,
                                 Metric metric,
                                 SecretStore secretStore,
                                 FlagSource flagSource) {
        this(tenantRepository,
             hostProvisionerProvider.getHostProvisioner(),
             infraDeployerProvider.getInfraDeployer(),
             configConvergenceChecker,
             httpProxy,
             EndpointsChecker.of(healthCheckers.getHealthChecker()),
             configserverConfig,
             orchestrator,
             new LogRetriever(),
             Clock.systemUTC(),
             testerClient,
             metric,
             new SecretStoreValidator(secretStore),
             new DefaultClusterReindexingStatusClient(),
             flagSource);
    }

    private ApplicationRepository(TenantRepository tenantRepository,
                                  Optional<Provisioner> hostProvisioner,
                                  Optional<InfraDeployer> infraDeployer,
                                  ConfigConvergenceChecker configConvergenceChecker,
                                  HttpProxy httpProxy,
                                  EndpointsChecker endpointsChecker,
                                  ConfigserverConfig configserverConfig,
                                  Orchestrator orchestrator,
                                  LogRetriever logRetriever,
                                  Clock clock,
                                  TesterClient testerClient,
                                  Metric metric,
                                  SecretStoreValidator secretStoreValidator,
                                  ClusterReindexingStatusClient clusterReindexingStatusClient,
                                  FlagSource flagSource) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.hostProvisioner = Objects.requireNonNull(hostProvisioner);
        this.infraDeployer = Objects.requireNonNull(infraDeployer);
        this.convergeChecker = Objects.requireNonNull(configConvergenceChecker);
        this.httpProxy = Objects.requireNonNull(httpProxy);
        this.endpointsChecker = Objects.requireNonNull(endpointsChecker);
        this.configserverConfig = Objects.requireNonNull(configserverConfig);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.logRetriever = Objects.requireNonNull(logRetriever);
        this.clock = Objects.requireNonNull(clock);
        this.testerClient = Objects.requireNonNull(testerClient);
        this.metric = Objects.requireNonNull(metric);
        this.secretStoreValidator = Objects.requireNonNull(secretStoreValidator);
        this.clusterReindexingStatusClient = clusterReindexingStatusClient;
        this.flagSource = flagSource;
    }

    // Should be used by tests only (first constructor in this class makes sure we use injectable components where possible)
    public static class Builder {
        private TenantRepository tenantRepository;
        private HttpProxy httpProxy = new HttpProxy(new SimpleHttpFetcher(Duration.ofSeconds(30)));
        private EndpointsChecker endpointsChecker = __ -> { throw new UnsupportedOperationException(); };
        private Clock clock = Clock.systemUTC();
        private ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder().build();
        private Orchestrator orchestrator;
        private LogRetriever logRetriever = new LogRetriever();
        private TesterClient testerClient = new TesterClient();
        private Metric metric = new NullMetric();
        private SecretStoreValidator secretStoreValidator = new SecretStoreValidator(new SecretStoreProvider().get());
        private FlagSource flagSource = new InMemoryFlagSource();
        private ConfigConvergenceChecker configConvergenceChecker = new ConfigConvergenceChecker();

        public Builder withTenantRepository(TenantRepository tenantRepository) {
            this.tenantRepository = tenantRepository;
            return this;
        }

        public Builder withClock(Clock clock) {
            this.clock = clock;
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

        public Builder withSecretStoreValidator(SecretStoreValidator secretStoreValidator) {
            this.secretStoreValidator = secretStoreValidator;
            return this;
        }

        public Builder withConfigConvergenceChecker(ConfigConvergenceChecker configConvergenceChecker) {
            this.configConvergenceChecker = configConvergenceChecker;
            return this;
        }

        public Builder withEndpointsChecker(EndpointsChecker endpointsChecker) {
            this.endpointsChecker = endpointsChecker;
            return this;
        }

        public ApplicationRepository build() {
            return new ApplicationRepository(tenantRepository,
                                             tenantRepository.hostProvisionerProvider().getHostProvisioner(),
                                             InfraDeployerProvider.empty().getInfraDeployer(),
                                             configConvergenceChecker,
                                             httpProxy,
                                             endpointsChecker,
                                             configserverConfig,
                                             orchestrator,
                                             logRetriever,
                                             clock,
                                             testerClient,
                                             metric,
                                             secretStoreValidator,
                                             ClusterReindexingStatusClient.DUMMY_INSTANCE,
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

    public PrepareResult prepare(long sessionId, PrepareParams prepareParams) {
        DeployHandlerLogger logger = DeployHandlerLogger.forPrepareParams(prepareParams);
        Deployment deployment = prepare(sessionId, prepareParams, logger);
        return new PrepareResult(sessionId, deployment.configChangeActions(), logger);
    }

    private Deployment prepare(long sessionId, PrepareParams prepareParams, DeployHandlerLogger logger) {
        Tenant tenant = getTenant(prepareParams.getApplicationId());
        Session session = validateThatLocalSessionIsNotActive(tenant, sessionId);
        Deployment deployment = Deployment.unprepared(session, this, hostProvisioner, tenant, prepareParams, logger, clock);
        deployment.prepare();
        logConfigChangeActions(deployment.configChangeActions(), logger);
        log.log(Level.INFO, TenantRepository.logPre(prepareParams.getApplicationId()) + "Session " + sessionId + " prepared successfully. ");
        return deployment;
    }

    public PrepareResult deploy(CompressedApplicationInputStream in, PrepareParams prepareParams) {
        DeployHandlerLogger logger = DeployHandlerLogger.forPrepareParams(prepareParams);
        File tempDir = uncheck(() -> Files.createTempDirectory("deploy")).toFile();
        ThreadLockStats threadLockStats = LockStats.getForCurrentThread();
        PrepareResult prepareResult;
        try {
            threadLockStats.startRecording("deploy of " + prepareParams.getApplicationId().serializedForm());
            prepareResult = deploy(decompressApplication(in, tempDir), prepareParams, logger);
        } finally {
            threadLockStats.stopRecording();
            cleanupTempDirectory(tempDir, logger);
        }
        return prepareResult;
    }

    public PrepareResult deploy(File applicationPackage, PrepareParams prepareParams) {
        return deploy(applicationPackage, prepareParams, DeployHandlerLogger.forPrepareParams(prepareParams));
    }

    private PrepareResult deploy(File applicationDir, PrepareParams prepareParams, DeployHandlerLogger logger) {
        long sessionId = createSession(prepareParams.getApplicationId(),
                                       prepareParams.getTimeoutBudget(),
                                       applicationDir,
                                       logger);
        Deployment deployment = prepare(sessionId, prepareParams, logger);

        if ( ! prepareParams.isDryRun())
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
        Optional<LocalSession> activeSession = getActiveLocalSession(tenant, application);
        if (activeSession.isEmpty()) return Optional.empty();
        TimeoutBudget timeoutBudget = new TimeoutBudget(clock, timeout);
        SessionRepository sessionRepository = tenant.getSessionRepository();
        DeployLogger logger = new SilentDeployLogger();
        Session newSession = sessionRepository.createSessionFromExisting(activeSession.get(), true, timeoutBudget, logger);

        return Optional.of(Deployment.unprepared(newSession, this, hostProvisioner, tenant, logger, timeout, clock,
                                                 false /* don't validate as this is already deployed */, bootstrap));
    }

    @Override
    public Optional<Instant> lastDeployTime(ApplicationId application) {
        Tenant tenant = tenantRepository.getTenant(application.tenant());
        if (tenant == null) return Optional.empty();
        Optional<Instant> activatedTime = getActiveSession(tenant, application).map(Session::getActivatedTime);
        log.log(Level.FINEST, application + " last activated " + activatedTime.orElse(Instant.EPOCH));
        return activatedTime;
    }

    public ApplicationId activate(Tenant tenant,
                                  long sessionId,
                                  TimeoutBudget timeoutBudget,
                                  boolean force) {
        DeployLogger logger = new SilentDeployLogger();
        Session session = getLocalSession(tenant, sessionId);
        Deployment deployment = Deployment.prepared(session, this, hostProvisioner, tenant, logger, timeoutBudget.timeout(), clock, false, force);
        deployment.activate();
        return session.getApplicationId();
    }

    public Transaction deactivateCurrentActivateNew(Optional<Session> active, Session prepared, boolean force) {
        Tenant tenant = tenantRepository.getTenant(prepared.getTenantName());
        Transaction transaction = tenant.getSessionRepository().createActivateTransaction(prepared);
        if (active.isPresent()) {
            checkIfActiveHasChanged(prepared, active.get(), force);
            checkIfActiveIsNewerThanSessionToBeActivated(prepared.getSessionId(), active.get().getSessionId());
            transaction.add(active.get().createDeactivateTransaction().operations());
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

    static void checkIfActiveHasChanged(Session session, Session activeSession, boolean ignoreStaleSessionFailure) {
        long activeSessionAtCreate = session.getActiveSessionAtCreate();
        log.log(Level.FINE, () -> activeSession.logPre() + "active session id at create time=" + activeSessionAtCreate);
        if (activeSessionAtCreate == 0) return; // No active session at create time

        long sessionId = session.getSessionId();
        long activeSessionSessionId = activeSession.getSessionId();
        log.log(Level.FINE, () -> activeSession.logPre() + "sessionId=" + sessionId +
                            ", current active session=" + activeSessionSessionId);
        if (activeSession.isNewerThan(activeSessionAtCreate) &&
            activeSessionSessionId != sessionId) {
            String errMsg = activeSession.logPre() + "Cannot activate session " +
                            sessionId + " because the currently active session (" +
                            activeSessionSessionId + ") has changed since session " + sessionId +
                            " was created (was " + activeSessionAtCreate + " at creation time)";
            if (ignoreStaleSessionFailure) {
                log.warning(errMsg + " (Continuing because of force.)");
            } else {
                throw new ActivationConflictException(errMsg);
            }
        }
    }

    // Config generation is equal to session id, and config generation must be a monotonically increasing number
    static void checkIfActiveIsNewerThanSessionToBeActivated(long sessionId, long currentActiveSessionId) {
        if (sessionId < currentActiveSessionId) {
            throw new ActivationConflictException("Cannot activate session " + sessionId +
                                                  ", because it is older than current active session (" +
                                                  currentActiveSessionId + ")");
        }
    }

    // ---------------- Application operations ----------------------------------------------------------------

    /**
     * Deletes an application and associated resources
     *
     * @return true if the application was found and deleted, false if it was not present
     * @throws RuntimeException if deleting the application fails. This method is exception safe.
     */
    public boolean delete(ApplicationId applicationId) {
        Tenant tenant = getTenant(applicationId);
        if (tenant == null) return false;

        TenantApplications tenantApplications = tenant.getApplicationRepo();
        NestedTransaction transaction = new NestedTransaction();
        Optional<ApplicationTransaction> applicationTransaction = hostProvisioner.map(provisioner -> provisioner.lock(applicationId))
                                                                                 .map(lock -> new ApplicationTransaction(lock, transaction));
        try (@SuppressWarnings("unused") var applicationLock = tenantApplications.lock(applicationId)) {
            Optional<Long> activeSession = tenantApplications.activeSessionOf(applicationId);
            CompletionWaiter waiter;
            if (activeSession.isPresent()) {
                try {
                    Session session = getRemoteSession(tenant, activeSession.get());
                    transaction.add(tenant.getSessionRepository().createSetStatusTransaction(session, Session.Status.DELETE));
                } catch (NotFoundException e) {
                    log.log(Level.INFO, TenantRepository.logPre(applicationId) + "Active session exists, but has not been deleted properly. Trying to cleanup");
                }
                waiter = tenantApplications.createRemoveApplicationWaiter(applicationId);
            } else {
                // If there's no active session, we still want to clean up any resources created in a failing prepare
                waiter = new NoopCompletionWaiter();
            }

            Curator curator = tenantRepository.getCurator();
            transaction.add(new ContainerEndpointsCache(tenant.getPath(), curator).delete(applicationId)); // TODO: Not unit tested
            // Delete any application roles
            transaction.add(new ApplicationRolesStore(curator, tenant.getPath()).delete(applicationId));
            // Delete endpoint certificates
            transaction.add(new EndpointCertificateMetadataStore(curator, tenant.getPath()).delete(applicationId));
            // This call will remove application in zookeeper. Watches in TenantApplications will remove the application
            // and allocated hosts in model and handlers in RPC server
            transaction.add(tenantApplications.createDeleteTransaction(applicationId));
            transaction.onCommitted(() -> log.log(Level.INFO, "Deleted " + applicationId));

            if (applicationTransaction.isPresent()) {
                hostProvisioner.get().remove(applicationTransaction.get());
                applicationTransaction.get().nested().commit();
            } else {
                transaction.commit();
            }

            // Wait for app being removed on other servers
            waiter.awaitCompletion(Duration.ofSeconds(30));

            return activeSession.isPresent();
        } finally {
            applicationTransaction.ifPresent(ApplicationTransaction::close);
        }
    }

    public HttpResponse proxyServiceHostnameRequest(ApplicationId applicationId, String hostName, String serviceName, HttpURL.Path path, Query query, HttpURL forwardedUrl) {
        return httpProxy.get(getApplication(applicationId), hostName, serviceName, path, query, forwardedUrl);
    }

    public Map<String, ClusterReindexing> getClusterReindexingStatus(ApplicationId applicationId) {
        return uncheck(() -> clusterReindexingStatusClient.getReindexingStatus(getApplication(applicationId)));
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

    public HttpResponse fileDistributionStatus(ApplicationId applicationId, Duration timeout) {
        return fileDistributionStatus.status(getApplication(applicationId), timeout);
    }

    public List<String> deleteUnusedFileDistributionReferences(FileDirectory fileDirectory, Duration keepFileReferencesDuration) {
        Set<String> fileReferencesInUse = getFileReferencesInUse();
        log.log(Level.FINE, () -> "File references in use : " + fileReferencesInUse);
        Instant instant = clock.instant().minus(keepFileReferencesDuration);
        log.log(Level.FINE, () -> "Remove unused file references last modified before " + instant);

        List<String> fileReferencesToDelete = sortedUnusedFileReferences(fileDirectory.getRoot(), fileReferencesInUse, instant);
        if (fileReferencesToDelete.size() > 0) {
            log.log(Level.FINE, () -> "Will delete file references not in use: " + fileReferencesToDelete);
            fileReferencesToDelete.forEach(fileReference -> fileDirectory.delete(new FileReference(fileReference), this::isFileReferenceInUse));
        }
        return fileReferencesToDelete;
    }

    private boolean isFileReferenceInUse(FileReference fileReference) {
        return getFileReferencesInUse().contains(fileReference.value());
    }

    private Set<String> getFileReferencesInUse() {
        Set<String> fileReferencesInUse = new HashSet<>();
        for (var applicationId : listApplications()) {
            Application app = getApplication(applicationId);
            fileReferencesInUse.addAll(app.getModel().fileReferences().stream()
                                          .map(FileReference::value)
                                          .collect(Collectors.toSet()));
        }
        return fileReferencesInUse;
    }

    private List<String> sortedUnusedFileReferences(File fileReferencesPath, Set<String> fileReferencesInUse, Instant instant) {
        Set<String> fileReferencesOnDisk = getFileReferencesOnDisk(fileReferencesPath);
        log.log(Level.FINE, () -> "File references on disk (in " + fileReferencesPath + "): " + fileReferencesOnDisk);
        return fileReferencesOnDisk
                .stream()
                .filter(fileReference -> ! fileReferencesInUse.contains(fileReference))
                .filter(fileReference -> isLastModifiedBefore(new File(fileReferencesPath, fileReference), instant))
                .sorted(Comparator.comparing(a -> lastModified(new File(fileReferencesPath, a))))
                .toList();
    }

    public Set<FileReference> getFileReferences(ApplicationId applicationId) {
        return getOptionalApplication(applicationId).map(app -> app.getModel().fileReferences()).orElse(Set.of());
    }

    public ApplicationFile getApplicationFileFromSession(TenantName tenantName, long sessionId, HttpURL.Path path, Session.Mode mode) {
        Tenant tenant = tenantRepository.getTenant(tenantName);
        return getLocalSession(tenant, sessionId).getApplicationFile(Path.from(path.segments()), mode);
    }

    public Tenant getTenant(ApplicationId applicationId) {
        return tenantRepository.getTenant(applicationId.tenant());
    }

    Application getApplication(ApplicationId applicationId) {
        return getApplication(applicationId, Optional.empty());
    }

    private Application getApplication(ApplicationId applicationId, Optional<Version> version) {
        Tenant tenant = getTenant(applicationId);
        if (tenant == null) throw new NotFoundException("Tenant '" + applicationId.tenant() + "' not found");

        Optional<ApplicationSet> activeApplicationSet = tenant.getSessionRepository().getActiveApplicationSet(applicationId);
        if (activeApplicationSet.isEmpty()) throw new NotFoundException("Unknown application id '" + applicationId + "'");

        return activeApplicationSet.get().getForVersionOrLatest(version, clock.instant());
    }

    // Will return Optional.empty() if getting application fails (instead of throwing an exception)
    private Optional<Application> getOptionalApplication(ApplicationId applicationId) {
        try {
            return Optional.of(getApplication(applicationId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<ApplicationId> listApplications() {
        return tenantRepository.getAllTenants().stream()
                .flatMap(tenant -> tenant.getApplicationRepo().activeApplications().stream())
                .toList();
    }

    private boolean isLastModifiedBefore(File fileReference, Instant instant) {
        return lastModified(fileReference).isBefore(instant);
    }

    private Instant lastModified(File fileReference) {
        BasicFileAttributes fileAttributes;
        try {
            fileAttributes = readAttributes(fileReference.toPath(), BasicFileAttributes.class);
            return fileAttributes.lastModifiedTime().toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<String> getApplicationPackageReference(ApplicationId applicationId) {
        Optional<String> applicationPackage = Optional.empty();
        Optional<Session> session = getActiveSession(applicationId);
        if (session.isPresent()) {
            FileReference applicationPackageReference = session.get().getApplicationPackageReference();
            File downloadDirectory = new File(Defaults.getDefaults().underVespaHome(configserverConfig().fileReferencesDir()));
            if (applicationPackageReference != null && ! fileReferenceExistsOnDisk(downloadDirectory, applicationPackageReference))
                applicationPackage = Optional.of(applicationPackageReference.value());
        }
        return applicationPackage;
    }

    public List<Version> getAllVersions(ApplicationId applicationId) {
        Optional<ApplicationSet> applicationSet = getActiveApplicationSet(applicationId);
        return applicationSet.isEmpty()
                ? List.of()
                : applicationSet.get().getAllVersions(applicationId);
    }

    public HttpResponse validateSecretStore(ApplicationId applicationId, SystemName systemName, Slime slime) {
        Application application = getApplication(applicationId);
        return secretStoreValidator.validateSecretStore(application, systemName, slime);
    }

    // ---------------- Convergence ----------------------------------------------------------------

    public ServiceResponse checkServiceForConfigConvergence(ApplicationId applicationId,
                                                            String hostAndPort,
                                                            Duration timeout,
                                                            Optional<Version> vespaVersion) {
        return convergeChecker.getServiceConfigGeneration(getApplication(applicationId, vespaVersion), hostAndPort, timeout);
    }

    public ServiceListResponse servicesToCheckForConfigConvergence(ApplicationId applicationId,
                                                                   Duration timeoutPerService,
                                                                   Optional<Version> vespaVersion) {
        return convergeChecker.checkConvergenceForAllServices(getApplication(applicationId, vespaVersion), timeoutPerService);
    }

    public ConfigConvergenceChecker configConvergenceChecker() { return convergeChecker; }

    public Availability verifyEndpoints(List<Endpoint> endpoints) {
        return endpointsChecker.endpointsAvailable(endpoints);
    }

    // ---------------- Logs ----------------------------------------------------------------

    public HttpResponse getLogs(ApplicationId applicationId, Optional<DomainName> hostname, String apiParams) {
        String logServerURI = getLogServerURI(applicationId, hostname) + apiParams;
        return logRetriever.getLogs(logServerURI, lastDeployTime(applicationId));
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

    public Activation activate(Session session, ApplicationId applicationId, Tenant tenant, boolean force) {
        NestedTransaction transaction = new NestedTransaction();
        Optional<ApplicationTransaction> applicationTransaction = hostProvisioner.map(provisioner -> provisioner.lock(applicationId))
                                                                                 .map(lock -> new ApplicationTransaction(lock, transaction));
        try (@SuppressWarnings("unused") var sessionLock = tenant.getApplicationRepo().lock(applicationId)) {
            Optional<Session> activeSession = getActiveSession(applicationId);
            var sessionZooKeeperClient = tenant.getSessionRepository().createSessionZooKeeperClient(session.getSessionId());
            CompletionWaiter waiter = sessionZooKeeperClient.createActiveWaiter();

            transaction.add(deactivateCurrentActivateNew(activeSession, session, force));
            if (applicationTransaction.isPresent()) {
                hostProvisioner.get().activate(session.getAllocatedHosts().getHosts(),
                                               new ActivationContext(session.getSessionId()),
                                               applicationTransaction.get());
                applicationTransaction.get().nested().commit();
            } else {
                transaction.commit();
            }
            return new Activation(waiter, activeSession);
        } finally {
            applicationTransaction.ifPresent(ApplicationTransaction::close);
        }
    }

    /**
     * Gets the active Session for the given application id.
     *
     * @return the active session, or null if there is no active session for the given application id.
     */
    public Optional<Session> getActiveSession(ApplicationId applicationId) {
        return getActiveRemoteSession(applicationId);
    }

    /**
     * Gets the active Session for the given application id.
     *
     * @return the active session, or null if there is no active session for the given application id.
     */
    public Optional<Session> getActiveRemoteSession(ApplicationId applicationId) {
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
        if (Session.Status.ACTIVATE == session.getStatus())
            throw new IllegalArgumentException("Session is active: " + sessionId);
    }

    public void validateThatSessionIsPrepared(Tenant tenant, long sessionId) {
        Session session = getRemoteSession(tenant, sessionId);
        if ( Session.Status.PREPARE != session.getStatus())
            throw new IllegalArgumentException("Session not prepared: " + sessionId);
    }

    public long createSessionFromExisting(ApplicationId applicationId,
                                          boolean internalRedeploy,
                                          TimeoutBudget timeoutBudget,
                                          DeployLogger deployLogger) {
        Tenant tenant = getTenant(applicationId);
        SessionRepository sessionRepository = tenant.getSessionRepository();
        Session fromSession = getExistingSession(tenant, applicationId);
        return sessionRepository.createSessionFromExisting(fromSession, internalRedeploy, timeoutBudget, deployLogger).getSessionId();
    }

    public long createSession(ApplicationId applicationId, TimeoutBudget timeoutBudget, InputStream in,
                              String contentType, DeployLogger logger) {
        File tempDir = uncheck(() -> Files.createTempDirectory("deploy")).toFile();
        long sessionId;
        try {
            sessionId = createSession(applicationId, timeoutBudget, decompressApplication(in, contentType, tempDir), logger);
        } finally {
            cleanupTempDirectory(tempDir, logger);
        }
        return sessionId;
    }

    public long createSession(ApplicationId applicationId, TimeoutBudget timeoutBudget, File applicationDirectory, DeployLogger deployLogger) {
        SessionRepository sessionRepository = getTenant(applicationId).getSessionRepository();
        Session session = sessionRepository.createSessionFromApplicationPackage(applicationDirectory, applicationId, timeoutBudget, deployLogger);
        return session.getSessionId();
    }

    public void deleteExpiredLocalSessions() {
        Map<Tenant, Collection<LocalSession>> sessionsPerTenant = new HashMap<>();
        tenantRepository.getAllTenants()
                        .forEach(tenant -> sessionsPerTenant.put(tenant, tenant.getSessionRepository().getLocalSessions()));

        Set<ApplicationId> applicationIds = new HashSet<>();
        sessionsPerTenant.values()
                .forEach(sessionList -> sessionList.stream()
                        .map(Session::getOptionalApplicationId)
                        .filter(Optional::isPresent)
                        .forEach(appId -> applicationIds.add(appId.get())));

        Map<ApplicationId, Long> activeSessions = new HashMap<>();
        applicationIds.forEach(applicationId -> getActiveSession(applicationId).ifPresent(session -> activeSessions.put(applicationId, session.getSessionId())));
        sessionsPerTenant.keySet().forEach(tenant -> tenant.getSessionRepository().deleteExpiredSessions(activeSessions));
    }

    public int deleteExpiredRemoteSessions(Clock clock) {
        return tenantRepository.getAllTenants()
                .stream()
                .map(tenant -> tenant.getSessionRepository().deleteExpiredRemoteSessions(clock))
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

    // ---------------- SearchNode Metrics ------------------------------------------------------------------------

    public SearchNodeMetricsResponse getSearchNodeMetrics(ApplicationId applicationId) {
        Application application = getApplication(applicationId);
        SearchNodeMetricsRetriever searchNodeMetricsRetriever = new SearchNodeMetricsRetriever();
        return searchNodeMetricsRetriever.getMetrics(application);
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

    private ApplicationCuratorDatabase requireDatabase(ApplicationId id) {
        Tenant tenant = getTenant(id);
        if (tenant == null)
            throw new NotFoundException("Tenant '" + id.tenant().value() + "' not found");

        return tenant.getApplicationRepo().database();
    }

    public ApplicationReindexing getReindexing(ApplicationId id) {
        return requireDatabase(id).readReindexingStatus(id)
                                  .orElseThrow(() -> new NotFoundException("Reindexing status not found for " + id));
    }

    public void modifyReindexing(ApplicationId id, UnaryOperator<ApplicationReindexing> modifications) {
        Tenant tenant = getTenant(id);
        if (tenant == null)
            throw new NotFoundException("Tenant '" + id.tenant().value() + "' not found");

        tenant.getApplicationRepo().database().modifyReindexing(id, ApplicationReindexing.empty(), modifications);
    }

    public ConfigserverConfig configserverConfig() {
        return configserverConfig;
    }

    public ApplicationId getApplicationIdForHostname(String hostname) {
        Optional<ApplicationId> applicationId = tenantRepository.getAllTenantNames().stream()
                .map(tenantName -> tenantRepository.getTenant(tenantName).getApplicationRepo().resolveApplicationId(hostname))
                .filter(Objects::nonNull)
                .findFirst();
        return applicationId.orElse(null);
    }

    public FlagSource flagSource() { return flagSource; }

    private Session validateThatLocalSessionIsNotActive(Tenant tenant, long sessionId) {
        Session session = getLocalSession(tenant, sessionId);
        if (Session.Status.ACTIVATE.equals(session.getStatus())) {
            throw new IllegalArgumentException("Session is active: " + sessionId);
        }
        return session;
    }

    private Session getLocalSession(Tenant tenant, long sessionId) {
        Session session = tenant.getSessionRepository().getLocalSession(sessionId);
        if (session == null) throw new NotFoundException("Session " + sessionId + " was not found");

        return session;
    }

    private RemoteSession getRemoteSession(Tenant tenant, long sessionId) {
        RemoteSession session = tenant.getSessionRepository().getRemoteSession(sessionId);
        if (session == null) throw new NotFoundException("Session " + sessionId + " was not found");

        return session;
    }

    public Optional<ApplicationSet> getActiveApplicationSet(ApplicationId appId) {
        return getTenant(appId).getSessionRepository().getActiveApplicationSet(appId);
    }

    public Application getActiveApplication(ApplicationId applicationId) {
        return getActiveApplicationSet(applicationId)
                .map(a -> a.getForVersionOrLatest(Optional.empty(), clock.instant()))
                .orElseThrow(() -> new RuntimeException("Found no active application for " + applicationId));
    }

    private File decompressApplication(InputStream in, String contentType, File tempDir) {
        try (CompressedApplicationInputStream application =
                     CompressedApplicationInputStream.createFromCompressedStream(in, contentType, configserverConfig.maxApplicationPackageSize())) {
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

    private void cleanupTempDirectory(File tempDir, DeployLogger logger) {
        if (!IOUtils.recursiveDeleteDir(tempDir)) {
            logger.log(Level.WARNING, "Not able to delete tmp dir '" + tempDir + "'");
        }
    }

    // TODO: Merge this and getActiveSession(), they are almost identical
    private Session getExistingSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return getRemoteSession(tenant, applicationRepo.requireActiveSessionOf(applicationId));
    }

    public Optional<Session> getActiveSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return applicationRepo.activeSessionOf(applicationId).map(aLong -> tenant.getSessionRepository().getRemoteSession(aLong));
    }

    public Optional<LocalSession> getActiveLocalSession(Tenant tenant, ApplicationId applicationId) {
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return applicationRepo.activeSessionOf(applicationId).map(aLong -> tenant.getSessionRepository().getLocalSession(aLong));
    }

    public double getQuotaUsageRate(ApplicationId applicationId) {
        var application = getApplication(applicationId);
        return application.getModel().provisioned().all().values().stream()
                .map(Capacity::maxResources)// TODO: This may be unspecified -> 0
                .mapToDouble(resources -> resources.nodes() * resources.nodeResources().cost())
                .sum();
    }

    @Override
    public Duration serverDeployTimeout() { return Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout()); }

    private void logConfigChangeActions(ConfigChangeActions actions, DeployLogger logger) {
        RestartActions restartActions = actions.getRestartActions();
        if ( ! restartActions.isEmpty()) {
            if (configserverConfig().hostedVespa())
                logger.log(Level.INFO, "Orchestrated service restart triggered due to change(s) from active to new application:\n" +
                                       restartActions.format());
            else
                logger.log(Level.WARNING, "Change(s) between active and new application that require restart:\n" +
                                          restartActions.format());
        }
        RefeedActions refeedActions = actions.getRefeedActions();
        if ( ! refeedActions.isEmpty()) {
            logger.logApplicationPackage(Level.WARNING,
                                         "Change(s) between active and new application that may require re-feed:\n" +
                                         refeedActions.format());
        }
        ReindexActions reindexActions = actions.getReindexActions();
        if ( ! reindexActions.isEmpty()) {
            if (configserverConfig().hostedVespa())
                logger.log(Level.INFO, "Re-indexing triggered due to change(s) from active to new application:\n" +
                                       reindexActions.format());
            else
                logger.log(Level.WARNING,
                           "Change(s) between active and new application that may require re-index:\n" +
                           reindexActions.format());
        }
    }

    private String getLogServerURI(ApplicationId applicationId, Optional<DomainName> hostname) {
        // Allow to get logs from a given hostname if the application is under the hosted-vespa tenant.
        // We make no validation that the hostname is actually allocated to the given application since
        // most applications under hosted-vespa are not known to the model and it's OK for a user to get
        // logs for any host if they are authorized for the hosted-vespa tenant.
        if (hostname.isPresent() && HOSTED_VESPA_TENANT.equals(applicationId.tenant())) {
            int port = List.of(InfrastructureApplication.CONFIG_SERVER.id(), InfrastructureApplication.CONTROLLER.id()).contains(applicationId) ? 19071 : 8080;
            return "http://" + hostname.get().value() + ":" + port + "/logs";
        }

        Application application = getApplication(applicationId);
        Collection<HostInfo> hostInfos = application.getModel().getHosts();

        HostInfo logServerHostInfo = hostInfos.stream()
                .filter(host -> host.getServices().stream()
                        .anyMatch(serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Could not find host info for logserver"));

        ServiceInfo logService = logServerHostInfo.getServices().stream()
                                                  .filter(service -> LOGSERVER_CONTAINER.serviceName.equals(service.getServiceType()))
                                                  .findFirst()
                                                  .or(() -> logServerHostInfo.getServices().stream()
                                                                             .filter(service -> CONTAINER.serviceName.equals(service.getServiceType()))
                                                                             .findFirst())
                                                  .orElseThrow(() -> new IllegalArgumentException("No container running on logserver host"));
        int port = servicePort(logService);
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

    public Clock clock() { return clock; }

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

    public static class Activation {

        private final CompletionWaiter waiter;
        private final OptionalLong sourceSessionId;

        public Activation(CompletionWaiter waiter, Optional<Session> sourceSession) {
            this.waiter = waiter;
            this.sourceSessionId = sourceSession.map(s -> OptionalLong.of(s.getSessionId())).orElse(OptionalLong.empty());
        }

        public void awaitCompletion(Duration timeout) {
            waiter.awaitCompletion(timeout);
        }

        /** The session ID this activation was based on, if any */
        public OptionalLong sourceSessionId() {
            return sourceSessionId;
        }

    }

    private static class NoopCompletionWaiter implements CompletionWaiter {

        @Override
        public void awaitCompletion(Duration timeout) {}

        @Override
        public void notifyCompletion() {}

    }

}
