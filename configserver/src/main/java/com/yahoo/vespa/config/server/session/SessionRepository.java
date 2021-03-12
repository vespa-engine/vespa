// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.io.IOUtils;
import com.yahoo.lang.SettableOptional;
import com.yahoo.path.Path;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.modelfactory.ActivatedModelsBuilder;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.server.zookeeper.SessionCounter;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.yolean.Exceptions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.zookeeper.KeeperException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import java.util.stream.Collectors;

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
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final FlagSource flagSource;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final Metrics metrics;
    private final MetricUpdater metricUpdater;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;
    private final SessionPreparer sessionPreparer;
    private final Path sessionsPath;
    private final TenantName tenantName;
    private final ConfigCurator configCurator;
    private final SessionCounter sessionCounter;
    private final SecretStore secretStore;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final ConfigserverConfig configserverConfig;
    private final ConfigServerDB configServerDB;
    private final Zone zone;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final TenantListener tenantListener;

    public SessionRepository(TenantName tenantName,
                             TenantApplications applicationRepo,
                             SessionPreparer sessionPreparer,
                             ConfigCurator configCurator,
                             Metrics metrics,
                             StripedExecutor<TenantName> zkWatcherExecutor,
                             PermanentApplicationPackage permanentApplicationPackage,
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
                             TenantListener tenantListener) {
        this.tenantName = tenantName;
        this.configCurator = configCurator;
        sessionCounter = new SessionCounter(configCurator, tenantName);
        this.sessionsPath = TenantRepository.getSessionsPath(tenantName);
        this.clock = clock;
        this.curator = configCurator.curator();
        this.sessionLifetime = Duration.ofSeconds(configserverConfig.sessionLifetime());
        this.zkWatcherExecutor = command -> zkWatcherExecutor.execute(tenantName, command);
        this.permanentApplicationPackage = permanentApplicationPackage;
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
        this.tenantListener = tenantListener;

        loadSessions(); // Needs to be done before creating cache below
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, zkCacheExecutor);
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
    }

    private void loadSessions() {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                                new DaemonThreadFactory("load-sessions-"));
        loadLocalSessions(executor);
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

    private void loadLocalSessions(ExecutorService executor) {
        File[] sessions = tenantFileSystemDirs.sessionsPath().listFiles(sessionApplicationsFilter);
        if (sessions == null) return;

        Map<Long, Future<?>> futures = new HashMap<>();
        for (File session : sessions) {
            long sessionId = Long.parseLong(session.getName());
            futures.put(sessionId, executor.submit(() -> createSessionFromId(sessionId)));
        }
        futures.forEach((sessionId, future) -> {
            try {
                future.get();
                log.log(Level.FINE, () -> "Local session " + sessionId + " loaded");
            } catch (ExecutionException | InterruptedException e) {
                log.log(Level.WARNING, "Could not load session " + sessionId, e);
            }
        });
    }

    public ConfigChangeActions prepareLocalSession(Session session, DeployLogger logger, PrepareParams params, Instant now) {
        applicationRepo.createApplication(params.getApplicationId()); // TODO jvenstad: This is wrong, but it has to be done now, since preparation can change the application ID of a session :(
        logger.log(Level.FINE, "Created application " + params.getApplicationId());
        long sessionId = session.getSessionId();
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
        Curator.CompletionWaiter waiter = sessionZooKeeperClient.createPrepareWaiter();
        Optional<ApplicationSet> activeApplicationSet = getActiveApplicationSet(params.getApplicationId());
        ConfigChangeActions actions = sessionPreparer.prepare(applicationRepo.getHostValidator(), logger, params,
                                                              activeApplicationSet, now, getSessionAppDir(sessionId),
                                                              session.getApplicationPackage(), sessionZooKeeperClient)
                .getConfigChangeActions();
        setPrepared(session);
        waiter.awaitCompletion(params.getTimeoutBudget().timeLeft());
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
                                                  TimeoutBudget timeoutBudget) {
        ApplicationId existingApplicationId = existingSession.getApplicationId();
        File existingApp = getSessionAppDir(existingSession.getSessionId());
        LocalSession session = createSessionFromApplication(existingApp, existingApplicationId, internalRedeploy, timeoutBudget);
        // Note: Setters below need to be kept in sync with calls in SessionPreparer.writeStateToZooKeeper()
        session.setApplicationId(existingApplicationId);
        session.setApplicationPackageReference(existingSession.getApplicationPackageReference());
        session.setVespaVersion(existingSession.getVespaVersion());
        session.setDockerImageRepository(existingSession.getDockerImageRepository());
        session.setAthenzDomain(existingSession.getAthenzDomain());
        session.setTenantSecretStores(existingSession.getTenantSecretStores());
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
    public LocalSession createSessionFromApplicationPackage(File applicationDirectory, ApplicationId applicationId, TimeoutBudget timeoutBudget) {
        applicationRepo.createApplication(applicationId);
        return createSessionFromApplication(applicationDirectory, applicationId, false, timeoutBudget);
    }

    /**
     * Creates a local session based on a remote session and the distributed application package.
     * Does not wait for session being created on other servers.
     */
    private void createLocalSession(File applicationFile, ApplicationId applicationId, long sessionId) {
        try {
            ApplicationPackage applicationPackage = createApplicationPackage(applicationFile, applicationId, sessionId, false);
            createLocalSession(sessionId, applicationPackage);
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    // Will delete session data in ZooKeeper and file system
    public void deleteLocalSession(LocalSession session) {
        long sessionId = session.getSessionId();
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
            deleteLocalSession(session);
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
        RemoteSession newSession = loadSessionIfActive(session).orElse(session);
        remoteSessionCache.put(sessionId, newSession);
        updateSessionStateWatcher(sessionId, newSession);
        return newSession;
    }

    public int deleteExpiredRemoteSessions(Clock clock, Duration expiryTime) {
        int deleted = 0;
        for (long sessionId : getRemoteSessionsFromZooKeeper()) {
            Session session = remoteSessionCache.get(sessionId);
            if (session == null) continue; // Internal sessions not in sync with zk, continue
            if (session.getStatus() == Session.Status.ACTIVATE) continue;
            if (sessionHasExpired(session.getCreateTime(), expiryTime, clock)) {
                log.log(Level.FINE, () -> "Remote session " + sessionId + " for " + tenantName + " has expired, deleting it");
                deleteRemoteSessionFromZooKeeper(session);
                deleted++;
            }
        }
        return deleted;
    }

    public void deactivateAndUpdateCache(RemoteSession remoteSession) {
        RemoteSession session = remoteSession.deactivated();
        remoteSessionCache.put(session.getSessionId(), session);
    }

    public void deleteRemoteSessionFromZooKeeper(Session session) {
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(session.getSessionId());
        Transaction transaction = sessionZooKeeperClient.deleteTransaction();
        transaction.commit();
        transaction.close();
    }

    private boolean sessionHasExpired(Instant created, Duration expiryTime, Clock clock) {
        return (created.plus(expiryTime).isBefore(clock.instant()));
    }

    private List<Long> getSessionListFromDirectoryCache(List<ChildData> children) {
        return getSessionList(children.stream()
                                      .map(child -> Path.fromString(child.getPath()).getName())
                                      .collect(Collectors.toList()));
    }

    private List<Long> getSessionList(List<String> children) {
        return children.stream().map(Long::parseLong).collect(Collectors.toList());
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
                log.log(Level.WARNING, "Could not load session " + sessionId, e);
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

    void activate(RemoteSession session) {
        long sessionId = session.getSessionId();
        Curator.CompletionWaiter waiter = createSessionZooKeeperClient(sessionId).getActiveWaiter();
        log.log(Level.FINE, () -> session.logPre() + "Activating " + sessionId);
        applicationRepo.activateApplication(ensureApplicationLoaded(session), sessionId);
        log.log(Level.FINE, () -> session.logPre() + "Notifying " + waiter);
        notifyCompletion(waiter, session);
        log.log(Level.INFO, session.logPre() + "Session activated: " + sessionId);
    }

    private Optional<RemoteSession> loadSessionIfActive(RemoteSession session) {
        for (ApplicationId applicationId : applicationRepo.activeApplications()) {
            if (applicationRepo.requireActiveSessionOf(applicationId) == session.getSessionId()) {
                log.log(Level.FINE, () -> "Found active application for session " + session.getSessionId() + " , loading it");
                applicationRepo.activateApplication(ensureApplicationLoaded(session), session.getSessionId());
                log.log(Level.INFO, session.logPre() + "Application activated successfully: " + applicationId + " (generation " + session.getSessionId() + ")");
                return Optional.ofNullable(remoteSessionCache.get(session.getSessionId()));
            }
        }
        return Optional.empty();
    }

    void prepareRemoteSession(RemoteSession session) {
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(session.getSessionId());
        Curator.CompletionWaiter waiter = sessionZooKeeperClient.getPrepareWaiter();
        ensureApplicationLoaded(session);
        notifyCompletion(waiter, session);
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
        updateSessionStateWatcher(sessionId, activated);

        return applicationSet;
    }

    void confirmUpload(Session session) {
        Curator.CompletionWaiter waiter = session.getSessionZooKeeperClient().getUploadWaiter();
        long sessionId = session.getSessionId();
        log.log(Level.FINE, "Notifying upload waiter for session " + sessionId);
        notifyCompletion(waiter, session);
        log.log(Level.FINE, "Done notifying upload for session " + sessionId);
    }

    void notifyCompletion(Curator.CompletionWaiter completionWaiter, Session session) {
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
                log.log(Level.FINE, "Not able to notify completion for session " + session.getSessionId() +
                                    " (" + completionWaiter + ")," +
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
                                                                    curator,
                                                                    metrics,
                                                                    permanentApplicationPackage,
                                                                    flagSource,
                                                                    secretStore,
                                                                    hostProvisionerProvider,
                                                                    configserverConfig,
                                                                    zone,
                                                                    modelFactoryRegistry,
                                                                    configDefinitionRepo,
                                                                    tenantListener);
        // Read hosts allocated on the config server instance which created this
        SettableOptional<AllocatedHosts> allocatedHosts = new SettableOptional<>(applicationPackage.getAllocatedHosts());

        return ApplicationSet.fromList(builder.buildModels(session.getApplicationId(),
                                                           sessionZooKeeperClient.readDockerImageRepository(),
                                                           sessionZooKeeperClient.readVespaVersion(),
                                                           applicationPackage,
                                                           allocatedHosts,
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
                case CHILD_ADDED:
                case CHILD_REMOVED:
                case CONNECTION_RECONNECTED:
                    sessionsChanged();
                    break;
                default:
                    break;
            }
        });
    }

    // ---------------- Common stuff ----------------------------------------------------------------

    public void deleteExpiredSessions(Map<ApplicationId, Long> activeSessions) {
        log.log(Level.FINE, () -> "Purging old sessions for tenant '" + tenantName + "'");
        Set<LocalSession> toDelete = new HashSet<>();
        try {
            for (LocalSession candidate : getLocalSessions()) {
                Instant createTime = candidate.getCreateTime();
                log.log(Level.FINE, () -> "Candidate session for deletion: " + candidate.getSessionId() + ", created: " + createTime);

                // Sessions with state other than ACTIVATE
                if (hasExpired(candidate) && !isActiveSession(candidate)) {
                    toDelete.add(candidate);
                } else if (createTime.plus(Duration.ofDays(1)).isBefore(clock.instant())) {
                    //  Sessions with state ACTIVATE, but which are not actually active
                    Optional<ApplicationId> applicationId = candidate.getOptionalApplicationId();
                    if (applicationId.isEmpty()) continue;
                    Long activeSession = activeSessions.get(applicationId.get());
                    if (activeSession == null || activeSession != candidate.getSessionId()) {
                        toDelete.add(candidate);
                        log.log(Level.INFO, "Deleted inactive session " + candidate.getSessionId() + " created " +
                                            createTime + " for '" + applicationId + "'");
                    }
                }
            }

            toDelete.forEach(this::deleteLocalSession);

            // Make sure to catch here, to avoid executor just dying in case of issues ...
        } catch (Throwable e) {
            log.log(Level.WARNING, "Error when purging old sessions ", e);
        }
        log.log(Level.FINE, () -> "Done purging old sessions");
    }

    private boolean hasExpired(LocalSession candidate) {
        return candidate.getCreateTime().plus(sessionLifetime).isBefore(clock.instant());
    }

    private boolean isActiveSession(LocalSession candidate) {
        return candidate.getStatus() == Session.Status.ACTIVATE;
    }

    private void ensureSessionPathDoesNotExist(long sessionId) {
        Path sessionPath = getSessionPath(sessionId);
        if (configCurator.exists(sessionPath.getAbsolute())) {
            throw new IllegalArgumentException("Path " + sessionPath.getAbsolute() + " already exists in ZooKeeper");
        }
    }

    private ApplicationPackage createApplication(File userDir,
                                                 File configApplicationDir,
                                                 ApplicationId applicationId,
                                                 long sessionId,
                                                 Optional<Long> currentlyActiveSessionId,
                                                 boolean internalRedeploy) {
        long deployTimestamp = System.currentTimeMillis();
        String user = System.getenv("USER");
        if (user == null) {
            user = "unknown";
        }
        DeployData deployData = new DeployData(user, userDir.getAbsolutePath(), applicationId, deployTimestamp,
                                               internalRedeploy, sessionId, currentlyActiveSessionId.orElse(nonExistingActiveSessionId));
        return FilesApplicationPackage.fromFileWithDeployData(configApplicationDir, deployData);
    }

    private LocalSession createSessionFromApplication(File applicationFile,
                                                      ApplicationId applicationId,
                                                      boolean internalRedeploy,
                                                      TimeoutBudget timeoutBudget) {
        long sessionId = getNextSessionId();
        try {
            ensureSessionPathDoesNotExist(sessionId);
            ApplicationPackage app = createApplicationPackage(applicationFile, applicationId, sessionId, internalRedeploy);
            log.log(Level.FINE, () -> TenantRepository.logPre(tenantName) + "Creating session " + sessionId + " in ZooKeeper");
            SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
            sessionZKClient.createNewSession(clock.instant());
            Curator.CompletionWaiter waiter = sessionZKClient.getUploadWaiter();
            LocalSession session = new LocalSession(tenantName, sessionId, app, sessionZKClient);
            waiter.awaitCompletion(Duration.ofSeconds(Math.min(60, timeoutBudget.timeLeft().getSeconds())));
            addLocalSession(session);
            return session;
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    private ApplicationPackage createApplicationPackage(File applicationFile,
                                                        ApplicationId applicationId,
                                                        long sessionId,
                                                        boolean internalRedeploy) throws IOException {
        // Synchronize to avoid threads trying to create an application package concurrently
        // (e.g. a maintainer and an external deployment)
        synchronized (monitor) {
            Optional<Long> activeSessionId = getActiveSessionId(applicationId);
            File userApplicationDir = getSessionAppDir(sessionId);
            copyApp(applicationFile, userApplicationDir);
            ApplicationPackage applicationPackage = createApplication(applicationFile,
                                                                      userApplicationDir,
                                                                      applicationId,
                                                                      sessionId,
                                                                      activeSessionId,
                                                                      internalRedeploy);
            applicationPackage.writeMetaData();
            return applicationPackage;
        }
    }

    public Optional<ApplicationSet> getActiveApplicationSet(ApplicationId appId) {
        return applicationRepo.activeSessionOf(appId).flatMap(this::getApplicationSet);
    }

    private Optional<ApplicationSet> getApplicationSet(long sessionId) {
        Optional<ApplicationSet> applicationSet = Optional.empty();
        try {
            RemoteSession session = getRemoteSession(sessionId);
            applicationSet = Optional.ofNullable(ensureApplicationLoaded(session));
        } catch (IllegalArgumentException e) {
            // Do nothing if we have no currently active session
        }
        return applicationSet;
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
            log.log(Level.FINE, "Moving " + tempDestinationDir + " to " + destinationDir.getAbsolutePath());
            Files.move(tempDestinationDir, destinationDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            // In case some of the operations above fail
            if (tempDestinationDir != null)
                IOUtils.recursiveDeleteDir(tempDestinationDir.toFile());
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
     * Returns a new local session for the given session id if it does not already exist.
     * Will also add the session to the local session cache if necessary
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
        if (fileReference != null) {
            File rootDir = new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));
            File sessionDir;
            FileDirectory fileDirectory = new FileDirectory(rootDir);
            try {
                sessionDir = fileDirectory.getFile(fileReference);
            } catch (IllegalArgumentException e) {
                // We cannot be guaranteed that the file reference exists (it could be that it has not
                // been downloaded yet), and e.g when bootstrapping we cannot throw an exception in that case
                log.log(Level.FINE, "File reference for session id " + sessionId + ": " + fileReference + " not found in " + fileDirectory);
                return;
            }
            ApplicationId applicationId = sessionZKClient.readApplicationId()
                    .orElseThrow(() -> new RuntimeException("Could not find application id for session " + sessionId));
            log.log(Level.FINE, () -> "Creating local session for tenant '" + tenantName + "' with session id " + sessionId);
            createLocalSession(sessionDir, applicationId, sessionId);
        }
    }

    private Optional<Long> getActiveSessionId(ApplicationId applicationId) {
        List<ApplicationId> applicationIds = applicationRepo.activeApplications();
        return applicationIds.contains(applicationId)
                ? Optional.of(applicationRepo.requireActiveSessionOf(applicationId))
                : Optional.empty();
    }

    private long getNextSessionId() {
        return sessionCounter.nextSessionId();
    }

    public Path getSessionPath(long sessionId) {
        return sessionsPath.append(String.valueOf(sessionId));
    }

    Path getSessionStatePath(long sessionId) {
        return getSessionPath(sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
    }

    private SessionZooKeeperClient createSessionZooKeeperClient(long sessionId) {
        String serverId = configserverConfig.serverId();
        return new SessionZooKeeperClient(curator, configCurator, tenantName, sessionId, serverId);
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

    private void updateSessionStateWatcher(long sessionId, RemoteSession remoteSession) {
        SessionStateWatcher sessionStateWatcher = sessionStateWatchers.get(sessionId);
        if (sessionStateWatcher == null) {
            Curator.FileCache fileCache = curator.createFileCache(getSessionStatePath(sessionId).getAbsolute(), false);
            fileCache.addListener(this::nodeChanged);
            sessionStateWatchers.put(sessionId, new SessionStateWatcher(fileCache, remoteSession, metricUpdater, zkWatcherExecutor, this));
        } else {
            sessionStateWatcher.updateRemoteSession(remoteSession);
        }
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
