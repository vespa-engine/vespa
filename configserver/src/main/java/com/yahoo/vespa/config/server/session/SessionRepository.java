// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;
import com.yahoo.vespa.config.server.modelfactory.ActivatedModelsBuilder;
import com.yahoo.vespa.config.server.modelfactory.AllocatedHostsFromAllModels;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.SessionCounter;
import com.yahoo.vespa.config.server.zookeeper.ZKApplication;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.LongFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.UnboundStringFlag;
import com.yahoo.yolean.Exceptions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.zookeeper.KeeperException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.curator.Curator.CompletionWaiter;
import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static java.nio.file.Files.readAttributes;

/**
 *
 * Session repository for a tenant. Stores session state in zookeeper and file system. There are two
 * different session types (RemoteSession and LocalSession).
 *
 * @author Ulf Lilleengen
 * @author hmusum
 *
 */
public class SessionRepository {

    private static final Logger log = Logger.getLogger(SessionRepository.class.getName());
    private static final FilenameFilter sessionApplicationsFilter = (dir, name) -> name.matches("\\d+");
    private static final long nonExistingActiveSessionId = 0;

    private final Object monitor = new Object();
    private final Map<Long, LocalSession> localSessionCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, RemoteSession> remoteSessionCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, SessionStateWatcher> sessionStateWatchers = Collections.synchronizedMap(new HashMap<>());
    private final Duration sessionLifetime;
    private final Clock clock;
    private final Curator curator;
    private final Executor zkWatcherExecutor;
    private final FileDistributionFactory fileDistributionFactory;
    private final FlagSource flagSource;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final Metrics metrics;
    private final MetricUpdater metricUpdater;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;
    private final SessionPreparer sessionPreparer;
    private final Path sessionsPath;
    private final TenantName tenantName;
    private final SessionCounter sessionCounter;
    private final SecretStore secretStore;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final ConfigserverConfig configserverConfig;
    private final ConfigServerDB configServerDB;
    private final Zone zone;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final int maxNodeSize;
    private final LongFlag expiryTimeFlag;

    public SessionRepository(TenantName tenantName,
                             TenantApplications applicationRepo,
                             SessionPreparer sessionPreparer,
                             Curator curator,
                             Metrics metrics,
                             StripedExecutor<TenantName> zkWatcherExecutor,
                             FileDistributionFactory fileDistributionFactory,
                             FlagSource flagSource,
                             ExecutorService zkCacheExecutor,
                             SecretStore secretStore,
                             HostProvisionerProvider hostProvisionerProvider,
                             ConfigserverConfig configserverConfig,
                             ConfigServerDB configServerDB,
                             Zone zone,
                             Clock clock,
                             ModelFactoryRegistry modelFactoryRegistry,
                             ConfigDefinitionRepo configDefinitionRepo,
                             int maxNodeSize) {
        this.tenantName = tenantName;
        sessionCounter = new SessionCounter(curator, tenantName);
        this.sessionsPath = TenantRepository.getSessionsPath(tenantName);
        this.clock = clock;
        this.curator = curator;
        this.sessionLifetime = Duration.ofSeconds(configserverConfig.sessionLifetime());
        this.zkWatcherExecutor = command -> zkWatcherExecutor.execute(tenantName, command);
        this.fileDistributionFactory = fileDistributionFactory;
        this.flagSource = flagSource;
        this.tenantFileSystemDirs = new TenantFileSystemDirs(configServerDB, tenantName);
        this.applicationRepo = applicationRepo;
        this.sessionPreparer = sessionPreparer;
        this.metrics = metrics;
        this.metricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(tenantName));
        this.secretStore = secretStore;
        this.hostProvisionerProvider = hostProvisionerProvider;
        this.configserverConfig = configserverConfig;
        this.configServerDB = configServerDB;
        this.zone = zone;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.configDefinitionRepo = configDefinitionRepo;
        this.maxNodeSize = maxNodeSize;
        expiryTimeFlag = PermanentFlags.CONFIG_SERVER_SESSION_EXPIRY_TIME.bindTo(flagSource);

        loadSessions(); // Needs to be done before creating cache below
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, zkCacheExecutor);
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
    }

    private void loadSessions() {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                                new DaemonThreadFactory("load-sessions-"));
        loadSessions(executor);
    }

    // For testing
    void loadSessions(ExecutorService executor) {
        loadRemoteSessions(executor);
        try {
            executor.shutdown();
            if ( ! executor.awaitTermination(1, TimeUnit.MINUTES))
                log.log(Level.INFO, "Executor did not terminate");
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Shutdown of executor for loading sessions failed: " + Exceptions.toMessageString(e));
        }
    }

    // ---------------- Local sessions ----------------------------------------------------------------

    public void addLocalSession(LocalSession session) {
        long sessionId = session.getSessionId();
        localSessionCache.put(sessionId, session);
        if (remoteSessionCache.get(sessionId) == null)
            createRemoteSession(sessionId);
    }

    public LocalSession getLocalSession(long sessionId) {
        return localSessionCache.get(sessionId);
    }

    /** Returns a copy of local sessions */
    public Collection<LocalSession> getLocalSessions() {
        return List.copyOf(localSessionCache.values());
    }

    private LocalSession getSessionFromFile(long sessionId) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        File sessionDir = getAndValidateExistingSessionAppDir(sessionId);
        ApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(sessionDir);
        return new LocalSession(tenantName, sessionId, applicationPackage, sessionZKClient);
    }

    public Set<Long> getLocalSessionsIdsFromFileSystem() {
        File[] sessions = tenantFileSystemDirs.sessionsPath().listFiles(sessionApplicationsFilter);
        if (sessions == null) return Set.of();

        Set<Long> sessionIds = new HashSet<>();
        for (File session : sessions) {
            long sessionId = Long.parseLong(session.getName());
            sessionIds.add(sessionId);
        }
        return sessionIds;
    }

    public ConfigChangeActions prepareLocalSession(Session session, DeployLogger logger, PrepareParams params, Instant now) {
        params.vespaVersion().ifPresent(version -> {
            if ( ! params.isBootstrap() && ! modelFactoryRegistry.allVersions().contains(version))
                throw new UnknownVespaVersionException("Vespa version '" + version + "' not known by this config server");
        });

        applicationRepo.createApplication(params.getApplicationId()); // TODO jvenstad: This is wrong, but it has to be done now, since preparation can change the application ID of a session :(
        logger.log(Level.FINE, "Created application " + params.getApplicationId());
        long sessionId = session.getSessionId();
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
        Optional<CompletionWaiter> waiter = params.isDryRun()
                ? Optional.empty()
                : Optional.of(sessionZooKeeperClient.createPrepareWaiter());
        Optional<ApplicationSet> activeApplicationSet = getActiveApplicationSet(params.getApplicationId());
        ConfigChangeActions actions = sessionPreparer.prepare(applicationRepo, logger, params,
                                                              activeApplicationSet, now, getSessionAppDir(sessionId),
                                                              session.getApplicationPackage(), sessionZooKeeperClient)
                .getConfigChangeActions();
        setPrepared(session);
        waiter.ifPresent(w -> w.awaitCompletion(params.getTimeoutBudget().timeLeft()));
        return actions;
    }

    /**
     * Creates a new deployment session from an already existing session.
     *
     * @param existingSession the session to use as base
     * @param internalRedeploy whether this session is for a system internal redeploy — not an application package change
     * @param timeoutBudget timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSessionFromExisting(Session existingSession,
                                                  boolean internalRedeploy,
                                                  TimeoutBudget timeoutBudget,
                                                  DeployLogger deployLogger) {
        ApplicationId existingApplicationId = existingSession.getApplicationId();
        File existingApp = getSessionAppDir(existingSession.getSessionId());
        LocalSession session = createSessionFromApplication(existingApp,
                                                            existingApplicationId,
                                                            internalRedeploy,
                                                            timeoutBudget,
                                                            deployLogger);
        // Note: Setters below need to be kept in sync with calls in SessionPreparer.writeStateToZooKeeper()
        session.setApplicationId(existingApplicationId);
        session.setApplicationPackageReference(existingSession.getApplicationPackageReference());
        session.setVespaVersion(existingSession.getVespaVersion());
        session.setDockerImageRepository(existingSession.getDockerImageRepository());
        session.setAthenzDomain(existingSession.getAthenzDomain());
        session.setQuota(existingSession.getQuota());
        session.setTenantSecretStores(existingSession.getTenantSecretStores());
        session.setOperatorCertificates(existingSession.getOperatorCertificates());
        session.setCloudAccount(existingSession.getCloudAccount());
        return session;
    }

    /**
     * Creates a new deployment session from an application package.
     *
     * @param applicationDirectory a File pointing to an application.
     * @param applicationId application id for this new session.
     * @param timeoutBudget Timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSessionFromApplicationPackage(File applicationDirectory,
                                                            ApplicationId applicationId,
                                                            TimeoutBudget timeoutBudget,
                                                            DeployLogger deployLogger) {
        applicationRepo.createApplication(applicationId);
        return createSessionFromApplication(applicationDirectory, applicationId, false, timeoutBudget, deployLogger);
    }

    /**
     * Creates a local session based on a remote session and the distributed application package.
     * Does not wait for session being created on other servers.
     */
    private void createLocalSession(File applicationFile, ApplicationId applicationId, long sessionId) {
        try {
            ApplicationPackage applicationPackage = createApplicationPackage(applicationFile, applicationId, sessionId, false, Optional.empty());
            createLocalSession(sessionId, applicationPackage);
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    // Will delete session data in ZooKeeper and file system
    public void deleteLocalSession(long sessionId) {
        log.log(Level.FINE, () -> "Deleting local session " + sessionId);
        SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null) watcher.close();
        localSessionCache.remove(sessionId);
        NestedTransaction transaction = new NestedTransaction();
        transaction.add(FileTransaction.from(FileOperations.delete(getSessionAppDir(sessionId).getAbsolutePath())));
        transaction.commit();
    }

    private void deleteAllSessions() {
        for (LocalSession session : getLocalSessions()) {
            deleteLocalSession(session.getSessionId());
        }
    }

    // ---------------- Remote sessions ----------------------------------------------------------------

    public RemoteSession getRemoteSession(long sessionId) {
        return remoteSessionCache.get(sessionId);
    }

    /** Returns a copy of remote sessions */
    public Collection<RemoteSession> getRemoteSessions() {
        return List.copyOf(remoteSessionCache.values());
    }

    public List<Long> getRemoteSessionsFromZooKeeper() {
        return getSessionList(curator.getChildren(sessionsPath));
    }

    public RemoteSession createRemoteSession(long sessionId) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        RemoteSession session = new RemoteSession(tenantName, sessionId, sessionZKClient);
        loadSessionIfActive(session);
        remoteSessionCache.put(sessionId, session);
        updateSessionStateWatcher(sessionId);
        return session;
    }

    public int deleteExpiredRemoteSessions(Clock clock) {
        Duration expiryTime = Duration.ofSeconds(expiryTimeFlag.value());
        List<Long> remoteSessionsFromZooKeeper = getRemoteSessionsFromZooKeeper();
        log.log(Level.FINE, () -> "Remote sessions for tenant " + tenantName + ": " + remoteSessionsFromZooKeeper);

        int deleted = 0;
        // Avoid deleting too many in one run
        int deleteMax = (int) Math.min(1000, Math.max(50, remoteSessionsFromZooKeeper.size() * 0.05));
        for (long sessionId : remoteSessionsFromZooKeeper) {
            Session session = remoteSessionCache.get(sessionId);
            if (session == null)
                session = new RemoteSession(tenantName, sessionId, createSessionZooKeeperClient(sessionId));
            if (session.getStatus() == Session.Status.ACTIVATE) continue;
            if (sessionHasExpired(session.getCreateTime(), expiryTime, clock)) {
                log.log(Level.FINE, () -> "Remote session " + sessionId + " for " + tenantName + " has expired, deleting it");
                deleteRemoteSessionFromZooKeeper(session);
                deleted++;
            }
            if (deleted >= deleteMax)
                break;
        }
        return deleted;
    }

    public void deactivateSession(long sessionId) {
        var s = remoteSessionCache.get(sessionId);
        if (s == null) return;

        remoteSessionCache.put(sessionId, s.deactivated());
    }

    public void deleteRemoteSessionFromZooKeeper(Session session) {
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(session.getSessionId());
        Transaction transaction = sessionZooKeeperClient.deleteTransaction();
        transaction.commit();
        transaction.close();
    }

    private boolean sessionHasExpired(Instant created, Duration expiryTime, Clock clock) {
        return created.plus(expiryTime).isBefore(clock.instant());
    }

    private List<Long> getSessionListFromDirectoryCache(List<ChildData> children) {
        return getSessionList(children.stream()
                                      .map(child -> Path.fromString(child.getPath()).getName())
                                      .toList());
    }

    private List<Long> getSessionList(List<String> children) {
        return children.stream().map(Long::parseLong).toList();
    }

    private void loadRemoteSessions(ExecutorService executor) throws NumberFormatException {
        Map<Long, Future<?>> futures = new HashMap<>();
        for (long sessionId : getRemoteSessionsFromZooKeeper()) {
            futures.put(sessionId, executor.submit(() -> sessionAdded(sessionId)));
        }
        futures.forEach((sessionId, future) -> {
            try {
                future.get();
                log.log(Level.FINE, () -> "Remote session " + sessionId + " loaded");
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Could not load remote session " + sessionId, e);
            }
        });
    }

    /**
     * A session for which we don't have a watcher, i.e. hitherto unknown to us.
     *
     * @param sessionId session id for the new session
     */
    public void sessionAdded(long sessionId) {
        if (hasStatusDeleted(sessionId)) return;

        log.log(Level.FINE, () -> "Adding remote session " + sessionId);
        Session session = createRemoteSession(sessionId);
        if (session.getStatus() == Session.Status.NEW) {
            log.log(Level.FINE, () -> session.logPre() + "Confirming upload for session " + sessionId);
            confirmUpload(session);
        }
        createLocalSessionFromDistributedApplicationPackage(sessionId);
    }

    private boolean hasStatusDeleted(long sessionId) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        RemoteSession session = new RemoteSession(tenantName, sessionId, sessionZKClient);
        return session.getStatus() == Session.Status.DELETE;
    }

    void activate(long sessionId) {
        createLocalSessionFromDistributedApplicationPackage(sessionId);
        RemoteSession session = remoteSessionCache.get(sessionId);
        if (session == null) return;

        CompletionWaiter waiter = createSessionZooKeeperClient(sessionId).getActiveWaiter();
        log.log(Level.FINE, () -> session.logPre() + "Activating " + sessionId);
        applicationRepo.activateApplication(ensureApplicationLoaded(session), sessionId);
        log.log(Level.FINE, () -> session.logPre() + "Notifying " + waiter);
        notifyCompletion(waiter);
        log.log(Level.INFO, session.logPre() + "Session activated: " + sessionId);
    }

    private void loadSessionIfActive(RemoteSession session) {
        for (ApplicationId applicationId : applicationRepo.activeApplications()) {
            Optional<Long> activeSession = applicationRepo.activeSessionOf(applicationId);
            if (activeSession.isPresent() && activeSession.get() == session.getSessionId()) {
                log.log(Level.FINE, () -> "Found active application for session " + session.getSessionId() + " , loading it");
                applicationRepo.activateApplication(ensureApplicationLoaded(session), session.getSessionId());
                log.log(Level.INFO, session.logPre() + "Application activated successfully: " + applicationId + " (generation " + session.getSessionId() + ")");
                return;
            }
        }
    }

    void prepareRemoteSession(long sessionId) {
        // Might need to create local session first
        createLocalSessionFromDistributedApplicationPackage(sessionId);
        RemoteSession session = remoteSessionCache.get(sessionId);
        if (session == null) return;

        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
        CompletionWaiter waiter = sessionZooKeeperClient.getPrepareWaiter();
        ensureApplicationLoaded(session);
        notifyCompletion(waiter);
    }

    public ApplicationSet ensureApplicationLoaded(RemoteSession session) {
        if (session.applicationSet().isPresent()) {
            return session.applicationSet().get();
        }
        Optional<Long> activeSessionId = getActiveSessionId(session.getApplicationId());
        Optional<ApplicationSet> previousApplicationSet = activeSessionId.filter(session::isNewerThan)
                                                                         .flatMap(this::getApplicationSet);
        ApplicationSet applicationSet = loadApplication(session, previousApplicationSet);
        RemoteSession activated = session.activated(applicationSet);
        long sessionId = activated.getSessionId();
        remoteSessionCache.put(sessionId, activated);
        updateSessionStateWatcher(sessionId);

        return applicationSet;
    }

    void confirmUpload(Session session) {
        CompletionWaiter waiter = createSessionZooKeeperClient(session.getSessionId()).getUploadWaiter();
        long sessionId = session.getSessionId();
        log.log(Level.FINE, () -> "Notifying upload waiter for session " + sessionId);
        notifyCompletion(waiter);
        log.log(Level.FINE, () -> "Done notifying upload for session " + sessionId);
    }

    void notifyCompletion(CompletionWaiter completionWaiter) {
        try {
            completionWaiter.notifyCompletion();
        } catch (RuntimeException e) {
            // Throw only if we get something else than NoNodeException or NodeExistsException.
            // NoNodeException might happen when the session is no longer in use (e.g. the app using this session
            // has been deleted) and this method has not been called yet for the previous session operation on a
            // minority of the config servers.
            // NodeExistsException might happen if an event for this node is delivered more than once, in that case
            // this is a no-op
            Set<Class<? extends KeeperException>> acceptedExceptions = Set.of(KeeperException.NoNodeException.class,
                                                                              KeeperException.NodeExistsException.class);
            Class<? extends Throwable> exceptionClass = e.getCause().getClass();
            if (acceptedExceptions.contains(exceptionClass))
                log.log(Level.FINE, () -> "Not able to notify completion for session (" + completionWaiter + ")," +
                                    " node " + (exceptionClass.equals(KeeperException.NoNodeException.class)
                        ? "has been deleted"
                        : "already exists"));
            else
                throw e;
        }
    }

    private ApplicationSet loadApplication(Session session, Optional<ApplicationSet> previousApplicationSet) {
        log.log(Level.FINE, () -> "Loading application for " + session);
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(session.getSessionId());
        ApplicationPackage applicationPackage = sessionZooKeeperClient.loadApplicationPackage();
        ActivatedModelsBuilder builder = new ActivatedModelsBuilder(session.getTenantName(),
                                                                    session.getSessionId(),
                                                                    sessionZooKeeperClient,
                                                                    previousApplicationSet,
                                                                    sessionPreparer.getExecutor(),
                                                                    curator,
                                                                    metrics,
                                                                    flagSource,
                                                                    secretStore,
                                                                    hostProvisionerProvider,
                                                                    configserverConfig,
                                                                    zone,
                                                                    modelFactoryRegistry,
                                                                    configDefinitionRepo);
        return ApplicationSet.fromList(builder.buildModels(session.getApplicationId(),
                                                           sessionZooKeeperClient.readDockerImageRepository(),
                                                           sessionZooKeeperClient.readVespaVersion(),
                                                           applicationPackage,
                                                           new AllocatedHostsFromAllModels(),
                                                           clock.instant()));
    }

    private void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            Multiset<Session.Status> sessionMetrics = HashMultiset.create();
            getRemoteSessions().forEach(session -> sessionMetrics.add(session.getStatus()));
            metricUpdater.setNewSessions(sessionMetrics.count(Session.Status.NEW));
            metricUpdater.setPreparedSessions(sessionMetrics.count(Session.Status.PREPARE));
            metricUpdater.setActivatedSessions(sessionMetrics.count(Session.Status.ACTIVATE));
            metricUpdater.setDeactivatedSessions(sessionMetrics.count(Session.Status.DEACTIVATE));
        });
    }

    @SuppressWarnings("unused")
    private void childEvent(CuratorFramework ignored, PathChildrenCacheEvent event) {
        zkWatcherExecutor.execute(() -> {
            log.log(Level.FINE, () -> "Got child event: " + event);
            switch (event.getType()) {
                case CHILD_ADDED, CHILD_REMOVED, CONNECTION_RECONNECTED -> sessionsChanged();
            }
        });
    }

    // ---------------- Common stuff ----------------------------------------------------------------

    public void deleteExpiredSessions(Map<ApplicationId, Long> activeSessions) {
        log.log(Level.FINE, () -> "Deleting expired local sessions for tenant '" + tenantName + "'");
        Set<Long> sessionIdsToDelete = new HashSet<>();
        Set<Long> newSessions = findNewSessionsInFileSystem();
        try {
            for (long sessionId : getLocalSessionsIdsFromFileSystem()) {
                // Skip sessions newly added (we might have a session in the file system, but not in ZooKeeper,
                // we don't want to touch any of them)
                if (newSessions.contains(sessionId))
                    continue;

                var sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
                Instant createTime = sessionZooKeeperClient.readCreateTime();
                Session.Status status = sessionZooKeeperClient.readStatus();

                log.log(Level.FINE, () -> "Candidate local session for deletion: " + sessionId +
                        ", created: " + createTime + ", status " + status + ", can be deleted: " + canBeDeleted(sessionId, status) +
                        ", hasExpired: " + hasExpired(createTime));

                if (hasExpired(createTime) && canBeDeleted(sessionId, status)) {
                    log.log(Level.FINE, () -> "expired: " + hasExpired(createTime) + ", can be deleted: " + canBeDeleted(sessionId, status));
                    sessionIdsToDelete.add(sessionId);
                } else if (createTime.plus(Duration.ofDays(1)).isBefore(clock.instant())) {
                    LocalSession session;
                    log.log(Level.FINE, () -> "not expired, but more than 1 day old: " + sessionId);
                    try {
                        session = getSessionFromFile(sessionId);
                    } catch (Exception e) {
                        log.log(Level.FINE, () -> "could not get session from file: " + sessionId + ": " + e.getMessage());
                        continue;
                    }
                    Optional<ApplicationId> applicationId = session.getOptionalApplicationId();
                    if (applicationId.isEmpty()) continue;

                    Long activeSession = activeSessions.get(applicationId.get());
                    if (activeSession == null || activeSession != sessionId) {
                        sessionIdsToDelete.add(sessionId);
                        log.log(Level.FINE, () -> "Will delete inactive session " + sessionId + " created " +
                                createTime + " for '" + applicationId + "'");
                    }
                }
            }

            sessionIdsToDelete.forEach(this::deleteLocalSession);
        } catch (Throwable e) { // Make sure to catch here, to avoid executor just dying in case of issues ...
            log.log(Level.WARNING, "Error when purging old sessions ", e);
        }
        log.log(Level.FINE, () -> "Done purging old sessions");
    }

    private boolean hasExpired(Instant created) {
        return created.plus(sessionLifetime).isBefore(clock.instant());
    }

    // Sessions with state other than UNKNOWN or ACTIVATE or old sessions in UNKNOWN state
    private boolean canBeDeleted(long sessionId, Session.Status status) {
        return ( ! List.of(Session.Status.UNKNOWN, Session.Status.ACTIVATE).contains(status))
                || oldSessionDirWithUnknownStatus(sessionId, status);
    }

    private boolean oldSessionDirWithUnknownStatus(long sessionId, Session.Status status) {
        Duration expiryTime = Duration.ofHours(configserverConfig.keepSessionsWithUnknownStatusHours());
        File sessionDir = tenantFileSystemDirs.getUserApplicationDir(sessionId);
        return sessionDir.exists()
                && status == Session.Status.UNKNOWN
                && created(sessionDir).plus(expiryTime).isBefore(clock.instant());
    }

    private Set<Long> findNewSessionsInFileSystem() {
        File[] sessions = tenantFileSystemDirs.sessionsPath().listFiles(sessionApplicationsFilter);
        Set<Long> newSessions = new HashSet<>();
        if (sessions != null) {
            for (File session : sessions) {
                try {
                    if (Files.getLastModifiedTime(session.toPath()).toInstant()
                             .isAfter(clock.instant().minus(Duration.ofSeconds(30))))
                        newSessions.add(Long.parseLong(session.getName()));
                } catch (IOException e) {
                    log.log(Level.FINE, "Unable to find last modified time for " + session.toPath());
                }
            }
        }
        return newSessions;
    }

    private Instant created(File file) {
        BasicFileAttributes fileAttributes;
        try {
            fileAttributes = readAttributes(file.toPath(), BasicFileAttributes.class);
            return fileAttributes.creationTime().toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensureSessionPathDoesNotExist(long sessionId) {
        Path sessionPath = getSessionPath(sessionId);
        if (curator.exists(sessionPath)) {
            throw new IllegalArgumentException("Path " + sessionPath.getAbsolute() + " already exists in ZooKeeper");
        }
    }

    private ApplicationPackage createApplication(File userDir,
                                                 File configApplicationDir,
                                                 ApplicationId applicationId,
                                                 long sessionId,
                                                 Optional<Long> currentlyActiveSessionId,
                                                 boolean internalRedeploy,
                                                 Optional<DeployLogger> deployLogger) {
        long deployTimestamp = System.currentTimeMillis();
        DeployData deployData = new DeployData(userDir.getAbsolutePath(), applicationId, deployTimestamp, internalRedeploy,
                                               sessionId, currentlyActiveSessionId.orElse(nonExistingActiveSessionId));
        FilesApplicationPackage app = FilesApplicationPackage.fromFileWithDeployData(configApplicationDir, deployData);
        validateFileExtensions(applicationId, deployLogger, app);

        return app;
    }

    private void validateFileExtensions(ApplicationId applicationId, Optional<DeployLogger> deployLogger, FilesApplicationPackage app) {
        try {
            app.validateFileExtensions();
        } catch (IllegalArgumentException e) {
            if (configserverConfig.hostedVespa()) {
                UnboundStringFlag flag = PermanentFlags.APPLICATION_FILES_WITH_UNKNOWN_EXTENSION;
                String value = flag.bindTo(flagSource).with(APPLICATION_ID, applicationId.serializedForm()).value();
                switch (value) {
                    case "FAIL" -> throw new InvalidApplicationException(e);
                    case "LOG" -> deployLogger.ifPresent(logger -> logger.logApplicationPackage(Level.WARNING, e.getMessage()));
                    default -> log.log(Level.WARNING, "Unknown value for flag " + flag.id() + ": " + value);
                }
            } else {
                deployLogger.ifPresent(logger -> logger.logApplicationPackage(Level.WARNING, e.getMessage()));
            }
        }
    }

    private LocalSession createSessionFromApplication(File applicationDirectory,
                                                      ApplicationId applicationId,
                                                      boolean internalRedeploy,
                                                      TimeoutBudget timeoutBudget,
                                                      DeployLogger deployLogger) {
        long sessionId = getNextSessionId();
        try {
            ensureSessionPathDoesNotExist(sessionId);
            ApplicationPackage app = createApplicationPackage(applicationDirectory, applicationId, sessionId, internalRedeploy, Optional.of(deployLogger));
            log.log(Level.FINE, () -> TenantRepository.logPre(tenantName) + "Creating session " + sessionId + " in ZooKeeper");
            SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
            sessionZKClient.createNewSession(clock.instant());
            CompletionWaiter waiter = sessionZKClient.getUploadWaiter();
            LocalSession session = new LocalSession(tenantName, sessionId, app, sessionZKClient);
            waiter.awaitCompletion(Duration.ofSeconds(Math.min(120, timeoutBudget.timeLeft().getSeconds())));
            addLocalSession(session);
            return session;
        } catch (IOException e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    private ApplicationPackage createApplicationPackage(File applicationDirectory,
                                                        ApplicationId applicationId,
                                                        long sessionId,
                                                        boolean internalRedeploy,
                                                        Optional<DeployLogger> deployLogger) throws IOException {
        // Synchronize to avoid threads trying to create an application package concurrently
        // (e.g. a maintainer and an external deployment)
        synchronized (monitor) {
            Optional<Long> activeSessionId = getActiveSessionId(applicationId);
            File userApplicationDir = getSessionAppDir(sessionId);
            copyApp(applicationDirectory, userApplicationDir);
            ApplicationPackage applicationPackage = createApplication(applicationDirectory,
                                                                      userApplicationDir,
                                                                      applicationId,
                                                                      sessionId,
                                                                      activeSessionId,
                                                                      internalRedeploy,
                                                                      deployLogger);
            applicationPackage.writeMetaData();
            return applicationPackage;
        }
    }

    public Optional<ApplicationSet> getActiveApplicationSet(ApplicationId appId) {
        return applicationRepo.activeSessionOf(appId).flatMap(this::getApplicationSet);
    }

    private Optional<ApplicationSet> getApplicationSet(long sessionId) {
        try {
            return Optional.ofNullable(getRemoteSession(sessionId)).map(this::ensureApplicationLoaded);
        } catch (IllegalArgumentException e) {
            // Do nothing if we have no currently active session
            return Optional.empty();
        }
    }

    private void copyApp(File sourceDir, File destinationDir) throws IOException {
        if (destinationDir.exists()) {
            log.log(Level.INFO, "Destination dir " + destinationDir + " already exists, app has already been copied");
            return;
        }
        if (! sourceDir.isDirectory())
            throw new IllegalArgumentException(sourceDir.getAbsolutePath() + " is not a directory");

        // Copy app atomically: Copy to a temp dir and move to destination
        java.nio.file.Path tempDestinationDir = null;
        try {
            tempDestinationDir = Files.createTempDirectory(destinationDir.getParentFile().toPath(), "app-package");
            log.log(Level.FINE, "Copying dir " + sourceDir.getAbsolutePath() + " to " + tempDestinationDir.toFile().getAbsolutePath());
            IOUtils.copyDirectory(sourceDir, tempDestinationDir.toFile());
            moveSearchDefinitionsToSchemasDir(tempDestinationDir);

            log.log(Level.FINE, "Moving " + tempDestinationDir + " to " + destinationDir.getAbsolutePath());
            Files.move(tempDestinationDir, destinationDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            // In case some operations above fail
            if (tempDestinationDir != null)
                IOUtils.recursiveDeleteDir(tempDestinationDir.toFile());
        }
    }

    // TODO: Remove on Vespa 9 (when we don't allow files in SEARCH_DEFINITIONS_DIR)
    // Copies schemas from searchdefinitions/ to schemas/ if searchdefinitions/ exists
    private void moveSearchDefinitionsToSchemasDir(java.nio.file.Path applicationDir) throws IOException {
        File schemasDir = applicationDir.resolve(ApplicationPackage.SCHEMAS_DIR.getRelative()).toFile();
        File sdDir = applicationDir.resolve(ApplicationPackage.SEARCH_DEFINITIONS_DIR.getRelative()).toFile();
        if (sdDir.exists() && sdDir.isDirectory()) {
            try {
                File[] sdFiles = sdDir.listFiles();
                if (sdFiles != null) {
                    Files.createDirectories(schemasDir.toPath());
                    Arrays.asList(sdFiles).forEach(file -> Exceptions.uncheck(
                            () -> Files.move(file.toPath(),
                                             schemasDir.toPath().resolve(file.toPath().getFileName()),
                                             StandardCopyOption.REPLACE_EXISTING)));
                }
                Files.delete(sdDir.toPath());
            } catch (IOException | UncheckedIOException e) {
                if (schemasDir.exists() && schemasDir.isDirectory())
                    throw new InvalidApplicationException(
                            "Both " + ApplicationPackage.SCHEMAS_DIR.getRelative() + "/ and " + ApplicationPackage.SEARCH_DEFINITIONS_DIR +
                                    "/ exist in application package, please remove " + ApplicationPackage.SEARCH_DEFINITIONS_DIR + "/", e);
                else
                    throw e;
            }
        }
    }

    /**
     * Returns a new session instance for the given session id.
     */
    void createSessionFromId(long sessionId) {
        File sessionDir = getAndValidateExistingSessionAppDir(sessionId);
        ApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(sessionDir);
        createLocalSession(sessionId, applicationPackage);
    }

    void createLocalSession(long sessionId, ApplicationPackage applicationPackage) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        LocalSession session = new LocalSession(tenantName, sessionId, applicationPackage, sessionZKClient);
        addLocalSession(session);
    }

    /**
     * Create a new local session for the given session id if it does not already exist and
     * will add the session to the local session cache. If there is no remote session matching
     * the session id the remote session will also be created.
     */
    public void createLocalSessionFromDistributedApplicationPackage(long sessionId) {
        if (applicationRepo.sessionExistsInFileSystem(sessionId)) {
            log.log(Level.FINE, () -> "Local session for session id " + sessionId + " already exists");
            createSessionFromId(sessionId);
            return;
        }

        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        FileReference fileReference = sessionZKClient.readApplicationPackageReference();
        log.log(Level.FINE, () -> "File reference for session id " + sessionId + ": " + fileReference);
        if (fileReference == null) return;

        File sessionDir;
        FileDirectory fileDirectory = fileDistributionFactory.fileDirectory();
        try {
            sessionDir = fileDirectory.getFile(fileReference);
        } catch (IllegalArgumentException e) {
            // We cannot be guaranteed that the file reference exists (it could be that it has not
            // been downloaded yet), and e.g. when bootstrapping we cannot throw an exception in that case
            log.log(Level.FINE, () -> "File reference for session id " + sessionId + ": " + fileReference + " not found");
            return;
        }
        ApplicationId applicationId = sessionZKClient.readApplicationId();
        log.log(Level.FINE, () -> "Creating local session for tenant '" + tenantName + "' with session id " + sessionId);
        createLocalSession(sessionDir, applicationId, sessionId);
    }

    private Optional<Long> getActiveSessionId(ApplicationId applicationId) {
        return applicationRepo.activeSessionOf(applicationId);
    }

    private long getNextSessionId() {
        return sessionCounter.nextSessionId();
    }

    public Path getSessionPath(long sessionId) {
        return sessionsPath.append(String.valueOf(sessionId));
    }

    Path getSessionStatePath(long sessionId) {
        return getSessionPath(sessionId).append(ZKApplication.SESSIONSTATE_ZK_SUBPATH);
    }

    public SessionZooKeeperClient createSessionZooKeeperClient(long sessionId) {
        return new SessionZooKeeperClient(curator,
                                          tenantName,
                                          sessionId,
                                          configserverConfig,
                                          fileDistributionFactory.createFileManager(getSessionAppDir(sessionId)),
                                          maxNodeSize);
    }

    private File getAndValidateExistingSessionAppDir(long sessionId) {
        File appDir = getSessionAppDir(sessionId);
        if (!appDir.exists() || !appDir.isDirectory()) {
            throw new IllegalArgumentException("Unable to find correct application directory for session " + sessionId);
        }
        return appDir;
    }

    private File getSessionAppDir(long sessionId) {
        return new TenantFileSystemDirs(configServerDB, tenantName).getUserApplicationDir(sessionId);
    }

    private void updateSessionStateWatcher(long sessionId) {
        sessionStateWatchers.computeIfAbsent(sessionId, (id) -> {
            Curator.FileCache fileCache = curator.createFileCache(getSessionStatePath(id).getAbsolute(), false);
            fileCache.addListener(this::nodeChanged);
            return new SessionStateWatcher(fileCache, id, metricUpdater, zkWatcherExecutor, this);
        });
    }

    @Override
    public String toString() {
        return getLocalSessions().toString();
    }

    public Clock clock() { return clock; }

    public void close() {
        deleteAllSessions();
        tenantFileSystemDirs.delete();
        try {
            if (directoryCache != null) {
                directoryCache.close();
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception when closing path cache", e);
        } finally {
            checkForRemovedSessions(new ArrayList<>());
        }
    }

    private void sessionsChanged() throws NumberFormatException {
        List<Long> sessions = getSessionListFromDirectoryCache(directoryCache.getCurrentData());
        checkForRemovedSessions(sessions);
        checkForAddedSessions(sessions);
    }

    private void checkForRemovedSessions(List<Long> existingSessions) {
        for (Iterator<RemoteSession> it = remoteSessionCache.values().iterator(); it.hasNext(); ) {
            long sessionId = it.next().sessionId;
            if (existingSessions.contains(sessionId)) continue;

            SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
            if (watcher != null) watcher.close();
            it.remove();
            metricUpdater.incRemovedSessions();
        }
    }

    private void checkForAddedSessions(List<Long> sessions) {
        for (Long sessionId : sessions)
            if (remoteSessionCache.get(sessionId) == null)
                sessionAdded(sessionId);
    }

    public Transaction createActivateTransaction(Session session) {
        Transaction transaction = createSetStatusTransaction(session, Session.Status.ACTIVATE);
        transaction.add(applicationRepo.createPutTransaction(session.getApplicationId(), session.getSessionId()).operations());
        return transaction;
    }

    public Transaction createSetStatusTransaction(Session session, Session.Status status) {
        return session.sessionZooKeeperClient.createWriteStatusTransaction(status);
    }

    void setPrepared(Session session) {
        session.setStatus(Session.Status.PREPARE);
    }

    private static class FileTransaction extends AbstractTransaction {

        public static FileTransaction from(FileOperation operation) {
            FileTransaction transaction = new FileTransaction();
            transaction.add(operation);
            return transaction;
        }

        @Override
        public void prepare() { }

        @Override
        public void commit() {
            for (Operation operation : operations())
                ((FileOperation)operation).commit();
        }

    }

    /** Factory for file operations */
    private static class FileOperations {

        /** Creates an operation which recursively deletes the given path */
        public static DeleteOperation delete(String pathToDelete) {
            return new DeleteOperation(pathToDelete);
        }

    }

    private interface FileOperation extends Transaction.Operation {

        void commit();

    }

    /**
     * Recursively deletes this path and everything below.
     * Succeeds with no action if the path does not exist.
     */
    private static class DeleteOperation implements FileOperation {

        private final String pathToDelete;

        DeleteOperation(String pathToDelete) {
            this.pathToDelete = pathToDelete;
        }

        @Override
        public void commit() {
            // TODO: Check delete access in prepare()
            IOUtils.recursiveDeleteDir(new File(pathToDelete));
        }

    }

}
