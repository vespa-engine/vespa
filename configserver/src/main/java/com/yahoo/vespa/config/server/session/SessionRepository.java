// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.server.zookeeper.SessionCounter;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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

    private final Map<Long, LocalSession> localSessionCache = new ConcurrentHashMap<>();
    private final Map<Long, RemoteSession> remoteSessionCache = new ConcurrentHashMap<>();
    private final Map<Long, SessionStateWatcher> sessionStateWatchers = new HashMap<>();
    private final Duration sessionLifetime;
    private final Clock clock;
    private final Curator curator;
    private final Executor zkWatcherExecutor;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final BooleanFlag distributeApplicationPackage;
    private final MetricUpdater metrics;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;
    private final SessionPreparer sessionPreparer;
    private final Path sessionsPath;
    private final TenantName tenantName;
    private final GlobalComponentRegistry componentRegistry;
    private final Path locksPath;

    public SessionRepository(TenantName tenantName,
                             GlobalComponentRegistry componentRegistry,
                             TenantApplications applicationRepo,
                             FlagSource flagSource,
                             SessionPreparer sessionPreparer) {
        this.tenantName = tenantName;
        this.componentRegistry = componentRegistry;
        this.sessionsPath = TenantRepository.getSessionsPath(tenantName);
        this.clock = componentRegistry.getClock();
        this.curator = componentRegistry.getCurator();
        this.sessionLifetime = Duration.ofSeconds(componentRegistry.getConfigserverConfig().sessionLifetime());
        this.zkWatcherExecutor = command -> componentRegistry.getZkWatcherExecutor().execute(tenantName, command);
        this.tenantFileSystemDirs = new TenantFileSystemDirs(componentRegistry.getConfigServerDB(), tenantName);
        this.applicationRepo = applicationRepo;
        this.sessionPreparer = sessionPreparer;
        this.distributeApplicationPackage = Flags.CONFIGSERVER_DISTRIBUTE_APPLICATION_PACKAGE.bindTo(flagSource);
        this.metrics = componentRegistry.getMetrics().getOrCreateMetricUpdater(Metrics.createDimensions(tenantName));
        this.locksPath = TenantRepository.getLocksPath(tenantName);
        loadSessions(); // Needs to be done before creating cache below
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, componentRegistry.getZkCacheExecutor());
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
    }

    private void loadSessions() {
        loadLocalSessions();
        initializeRemoteSessions();
    }

    // ---------------- Local sessions ----------------------------------------------------------------

    public synchronized void addLocalSession(LocalSession session) {
        long sessionId = session.getSessionId();
        localSessionCache.put(sessionId, session);
        if (remoteSessionCache.get(sessionId) == null) {
            createRemoteSession(sessionId);
        }
    }

    public LocalSession getLocalSession(long sessionId) {
        return localSessionCache.get(sessionId);
    }

    public Collection<LocalSession> getLocalSessions() {
        return localSessionCache.values();
    }

    private void loadLocalSessions() {
        File[] sessions = tenantFileSystemDirs.sessionsPath().listFiles(sessionApplicationsFilter);
        if (sessions == null) return;

        for (File session : sessions) {
            try {
                addLocalSession(createSessionFromId(Long.parseLong(session.getName())));
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Could not load session '" +
                        session.getAbsolutePath() + "':" + e.getMessage() + ", skipping it.");
            }
        }
    }

    public ConfigChangeActions prepareLocalSession(LocalSession session,
                                                   DeployLogger logger,
                                                   PrepareParams params,
                                                   Path tenantPath,
                                                   Instant now) {
        applicationRepo.createApplication(params.getApplicationId()); // TODO jvenstad: This is wrong, but it has to be done now, since preparation can change the application ID of a session :(
        logger.log(Level.FINE, "Created application " + params.getApplicationId());
        long sessionId = session.getSessionId();
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
        Optional<ApplicationSet> activeApplicationSet = getActiveApplicationSet(params.getApplicationId());
        ConfigChangeActions actions = sessionPreparer.prepare(applicationRepo.getHostValidator(), logger, params,
                                                              activeApplicationSet, tenantPath, now,
                                                              getSessionAppDir(sessionId),
                                                              session.getApplicationPackage(), sessionZooKeeperClient)
                .getConfigChangeActions();
        setPrepared(session);
        sessionZooKeeperClient.createPrepareWaiter().awaitCompletion(params.getTimeoutBudget().timeLeft());
        return actions;
    }

    public void deleteExpiredSessions(Map<ApplicationId, Long> activeSessions) {
        log.log(Level.FINE, () -> "Purging old sessions for tenant '" + tenantName + "'");
        try {
            for (LocalSession candidate : localSessionCache.values()) {
                Instant createTime = candidate.getCreateTime();
                log.log(Level.FINE, () -> "Candidate session for deletion: " + candidate.getSessionId() + ", created: " + createTime);

                // Sessions with state other than ACTIVATE
                if (hasExpired(candidate) && !isActiveSession(candidate)) {
                    deleteLocalSession(candidate);
                } else if (createTime.plus(Duration.ofDays(1)).isBefore(clock.instant())) {
                    //  Sessions with state ACTIVATE, but which are not actually active
                    Optional<ApplicationId> applicationId = candidate.getOptionalApplicationId();
                    if (applicationId.isEmpty()) continue;
                    Long activeSession = activeSessions.get(applicationId.get());
                    if (activeSession == null || activeSession != candidate.getSessionId()) {
                        deleteLocalSession(candidate);
                        log.log(Level.INFO, "Deleted inactive session " + candidate.getSessionId() + " created " +
                                            createTime + " for '" + applicationId + "'");
                    }
                }
            }
            // Make sure to catch here, to avoid executor just dying in case of issues ...
        } catch (Throwable e) {
            log.log(Level.WARNING, "Error when purging old sessions ", e);
        }
        log.log(Level.FINE, () -> "Done purging old sessions");
    }

    private boolean hasExpired(LocalSession candidate) {
        return (candidate.getCreateTime().plus(sessionLifetime).isBefore(clock.instant()));
    }

    private boolean isActiveSession(LocalSession candidate) {
        return candidate.getStatus() == Session.Status.ACTIVATE;
    }

    // Will delete session data in ZooKeeper and file system
    public void deleteLocalSession(LocalSession session) {
        long sessionId = session.getSessionId();
        try (Lock lock = lock(sessionId)) {
            log.log(Level.FINE, () -> "Deleting local session " + sessionId);
            SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
            if (watcher != null) watcher.close();
            localSessionCache.remove(sessionId);
            deletePersistentData(sessionId);
        }
    }

    private void deletePersistentData(long sessionId) {
        NestedTransaction transaction = new NestedTransaction();
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);

        // We will try to delete data from zookeeper from several servers, but since we take a lock
        // and the transaction will either delete everything or nothing (which will happen if it has been done
        // on another server) this works fine
        transaction.add(sessionZooKeeperClient.deleteTransaction(), FileTransaction.class);

        transaction.add(FileTransaction.from(FileOperations.delete(getSessionAppDir(sessionId).getAbsolutePath())));
        transaction.commit();
    }

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

    private void deleteAllSessions() {
        List<LocalSession> sessions = new ArrayList<>(localSessionCache.values());
        for (LocalSession session : sessions) {
            deleteLocalSession(session);
        }
    }

    // ---------------- Remote sessions ----------------------------------------------------------------

    public RemoteSession getRemoteSession(long sessionId) {
        return remoteSessionCache.get(sessionId);
    }

    public List<Long> getRemoteSessions() {
        return getSessionList(curator.getChildren(sessionsPath));
    }

    public int deleteExpiredRemoteSessions(Clock clock, Duration expiryTime) {
        int deleted = 0;
        for (long sessionId : getRemoteSessions()) {
            RemoteSession session = remoteSessionCache.get(sessionId);
            if (session == null) continue; // Internal sessions not in sync with zk, continue
            if (session.getStatus() == Session.Status.ACTIVATE) continue;
            if (sessionHasExpired(session.getCreateTime(), expiryTime, clock)) {
                log.log(Level.FINE, () -> "Remote session " + sessionId + " for " + tenantName + " has expired, deleting it");
                session.delete();
                deleted++;
            }
        }
        return deleted;
    }

    public int deleteExpiredLocks(Clock clock, Duration expiryTime) {
        int deleted = 0;
        for (var lock : curator.getChildren(locksPath)) {
            Path path = locksPath.append(lock);
            if (zooKeeperNodeCreated(path).orElse(clock.instant()).isBefore(clock.instant().minus(expiryTime))) {
                log.log(Level.FINE, () -> "Lock  " + path + " has expired, deleting it");
                curator.delete(path);
                deleted++;
            }
        }
        return deleted;
    }

    private Optional<Instant> zooKeeperNodeCreated(Path path) {
        return curator.getStat(path).map(s -> Instant.ofEpochMilli(s.getCtime()));
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

    private void initializeRemoteSessions() throws NumberFormatException {
        getRemoteSessions().forEach(this::sessionAdded);
    }

    private synchronized void sessionsChanged() throws NumberFormatException {
        List<Long> sessions = getSessionListFromDirectoryCache(directoryCache.getCurrentData());
        checkForRemovedSessions(sessions);
        checkForAddedSessions(sessions);
    }

    private void checkForRemovedSessions(List<Long> sessions) {
        for (RemoteSession session : remoteSessionCache.values())
            if ( ! sessions.contains(session.getSessionId()))
                sessionRemoved(session.getSessionId());
    }

    private void checkForAddedSessions(List<Long> sessions) {
        for (Long sessionId : sessions)
            if (remoteSessionCache.get(sessionId) == null)
                sessionAdded(sessionId);
    }

    /**
     * A session for which we don't have a watcher, i.e. hitherto unknown to us.
     *
     * @param sessionId session id for the new session
     */
    public synchronized void sessionAdded(long sessionId) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        if (sessionZKClient.readStatus().equals(Session.Status.DELETE)) return;

        log.log(Level.FINE, () -> "Adding remote session " + sessionId);
        RemoteSession session = createRemoteSession(sessionId);
        if (session.getStatus() == Session.Status.NEW) {
            log.log(Level.FINE, () -> session.logPre() + "Confirming upload for session " + sessionId);
            session.confirmUpload();
        } else {
            log.log(Level.WARNING, () -> session.logPre() + "Session " + sessionId + " added, but with unexpected status " + session.getStatus());
        }
        if (distributeApplicationPackage())
            createLocalSessionUsingDistributedApplicationPackage(sessionId);
    }

    void activate(RemoteSession session) {
        long sessionId = session.getSessionId();
        Curator.CompletionWaiter waiter = createSessionZooKeeperClient(sessionId).getActiveWaiter();
        log.log(Level.FINE, () -> session.logPre() + "Getting session from repo: " + sessionId);
        ApplicationSet app = session.ensureApplicationLoaded();
        log.log(Level.FINE, () -> session.logPre() + "Reloading config for " + sessionId);
        applicationRepo.reloadConfig(app);
        log.log(Level.FINE, () -> session.logPre() + "Notifying " + waiter);
        session.notifyCompletion(waiter);
        log.log(Level.INFO, session.logPre() + "Session activated: " + sessionId);
    }

    public void deactivate(RemoteSession remoteSession) {
        remoteSession.deactivate();
    }

    public void delete(RemoteSession remoteSession) {
        // This change will be picked up by directoryCache in this class, which will do the rest of
        // the cleanup on all config servers
        long sessionId = remoteSession.getSessionId();
        try (Lock lock = lock(sessionId)) {
            // TODO: Change log level to FINE when debugging is finished
            log.log(Level.INFO, () -> remoteSession.logPre() + "Deactivating and deleting remote session " + sessionId);
            remoteSession.deactivate();
            remoteSession.delete();
            remoteSessionCache.remove(sessionId);
        }
        LocalSession localSession = getLocalSession(sessionId);
        if (localSession != null) {
            // TODO: Change log level to FINE when debugging is finished
            log.log(Level.INFO, () -> localSession.logPre() + "Deleting local session " + sessionId);
            deleteLocalSession(localSession);
        }
    }

    void prepare(RemoteSession session) {
        session.prepare();
    }

    boolean distributeApplicationPackage() {
        return distributeApplicationPackage.value();
    }

    private void sessionRemoved(long sessionId) {
        SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null) watcher.close();
        remoteSessionCache.remove(sessionId);
        metrics.incRemovedSessions();
    }

    private void loadSessionIfActive(RemoteSession session) {
        for (ApplicationId applicationId : applicationRepo.activeApplications()) {
            if (applicationRepo.requireActiveSessionOf(applicationId) == session.getSessionId()) {
                log.log(Level.FINE, () -> "Found active application for session " + session.getSessionId() + " , loading it");
                applicationRepo.reloadConfig(session.ensureApplicationLoaded());
                log.log(Level.INFO, session.logPre() + "Application activated successfully: " + applicationId + " (generation " + session.getSessionId() + ")");
                return;
            }
        }
    }

    private void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            Multiset<Session.Status> sessionMetrics = HashMultiset.create();
            for (RemoteSession session : remoteSessionCache.values()) {
                sessionMetrics.add(session.getStatus());
            }
            metrics.setNewSessions(sessionMetrics.count(Session.Status.NEW));
            metrics.setPreparedSessions(sessionMetrics.count(Session.Status.PREPARE));
            metrics.setActivatedSessions(sessionMetrics.count(Session.Status.ACTIVATE));
            metrics.setDeactivatedSessions(sessionMetrics.count(Session.Status.DEACTIVATE));
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

    /**
     * Creates a new deployment session from an application package.
     *
     * @param applicationDirectory a File pointing to an application.
     * @param applicationId application id for this new session.
     * @param timeoutBudget Timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSession(File applicationDirectory, ApplicationId applicationId, TimeoutBudget timeoutBudget) {
        applicationRepo.createApplication(applicationId);
        Optional<Long> activeSessionId = applicationRepo.activeSessionOf(applicationId);
        return create(applicationDirectory, applicationId, activeSessionId, false, timeoutBudget);
    }

    public synchronized RemoteSession createRemoteSession(long sessionId) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        RemoteSession session = new RemoteSession(tenantName, sessionId, componentRegistry, sessionZKClient);
        remoteSessionCache.put(sessionId, session);
        loadSessionIfActive(session);
        addSessionStateWatcher(sessionId, session);
        return session;
    }

    private void ensureSessionPathDoesNotExist(long sessionId) {
        Path sessionPath = getSessionPath(sessionId);
        if (componentRegistry.getConfigCurator().exists(sessionPath.getAbsolute())) {
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

    private LocalSession createSessionFromApplication(ApplicationPackage applicationPackage,
                                                      long sessionId,
                                                      TimeoutBudget timeoutBudget,
                                                      Clock clock) {
        log.log(Level.FINE, () -> TenantRepository.logPre(tenantName) + "Creating session " + sessionId + " in ZooKeeper");
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        sessionZKClient.createNewSession(clock.instant());
        Curator.CompletionWaiter waiter = sessionZKClient.getUploadWaiter();
        LocalSession session = new LocalSession(tenantName, sessionId, applicationPackage, sessionZKClient);
        waiter.awaitCompletion(timeoutBudget.timeLeft());
        return session;
    }

    /**
     * Creates a new deployment session from an already existing session.
     *
     * @param existingSession the session to use as base
     * @param logger a deploy logger where the deploy log will be written.
     * @param internalRedeploy whether this session is for a system internal redeploy — not an application package change
     * @param timeoutBudget timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSessionFromExisting(Session existingSession,
                                                  DeployLogger logger,
                                                  boolean internalRedeploy,
                                                  TimeoutBudget timeoutBudget) {
        File existingApp = getSessionAppDir(existingSession.getSessionId());
        ApplicationId existingApplicationId = existingSession.getApplicationId();

        Optional<Long> activeSessionId = getActiveSessionId(existingApplicationId);
        logger.log(Level.FINE, "Create new session for application id '" + existingApplicationId + "' from existing active session " + activeSessionId);
        LocalSession session = create(existingApp, existingApplicationId, activeSessionId, internalRedeploy, timeoutBudget);
        // Note: Needs to be kept in sync with calls in SessionPreparer.writeStateToZooKeeper()
        session.setApplicationId(existingApplicationId);
        if (distributeApplicationPackage() && existingSession.getApplicationPackageReference() != null) {
            session.setApplicationPackageReference(existingSession.getApplicationPackageReference());
        }
        session.setVespaVersion(existingSession.getVespaVersion());
        session.setDockerImageRepository(existingSession.getDockerImageRepository());
        session.setAthenzDomain(existingSession.getAthenzDomain());
        return session;
    }

    private LocalSession create(File applicationFile, ApplicationId applicationId, Optional<Long> currentlyActiveSessionId,
                                boolean internalRedeploy, TimeoutBudget timeoutBudget) {
        long sessionId = getNextSessionId();
        try {
            ensureSessionPathDoesNotExist(sessionId);
            ApplicationPackage app = createApplicationPackage(applicationFile, applicationId,
                                                              sessionId, currentlyActiveSessionId, internalRedeploy);
            return createSessionFromApplication(app, sessionId, timeoutBudget, clock);
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    /**
     * This method is used when creating a session based on a remote session and the distributed application package
     * It does not wait for session being created on other servers
     */
    private LocalSession createLocalSession(File applicationFile, ApplicationId applicationId, long sessionId) {
        try {
            Optional<Long> currentlyActiveSessionId = getActiveSessionId(applicationId);
            ApplicationPackage applicationPackage = createApplicationPackage(applicationFile, applicationId,
                                                                             sessionId, currentlyActiveSessionId, false);
            SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
            return new LocalSession(tenantName, sessionId, applicationPackage, sessionZooKeeperClient);
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    private ApplicationPackage createApplicationPackage(File applicationFile, ApplicationId applicationId,
                                                        long sessionId, Optional<Long> currentlyActiveSessionId,
                                                        boolean internalRedeploy) throws IOException {
        File userApplicationDir = getSessionAppDir(sessionId);
        copyApp(applicationFile, userApplicationDir);
        ApplicationPackage applicationPackage = createApplication(applicationFile,
                                                                  userApplicationDir,
                                                                  applicationId,
                                                                  sessionId,
                                                                  currentlyActiveSessionId,
                                                                  internalRedeploy);
        applicationPackage.writeMetaData();
        return applicationPackage;
    }

    public Optional<ApplicationSet> getActiveApplicationSet(ApplicationId appId) {
        Optional<ApplicationSet> currentActiveApplicationSet = Optional.empty();
        try {
            long currentActiveSessionId = applicationRepo.requireActiveSessionOf(appId);
            RemoteSession currentActiveSession = getRemoteSession(currentActiveSessionId);
            currentActiveApplicationSet = Optional.ofNullable(currentActiveSession.ensureApplicationLoaded());
        } catch (IllegalArgumentException e) {
            // Do nothing if we have no currently active session
        }
        return currentActiveApplicationSet;
    }

    private void copyApp(File sourceDir, File destinationDir) throws IOException {
        if (destinationDir.exists())
            throw new RuntimeException("Destination dir " + destinationDir + " already exists");
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
    LocalSession createSessionFromId(long sessionId) {
        File sessionDir = getAndValidateExistingSessionAppDir(sessionId);
        ApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(sessionDir);
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        return new LocalSession(tenantName, sessionId, applicationPackage, sessionZKClient);
    }

    /**
     * Returns a new local session for the given session id if it does not already exist.
     * Will also add the session to the local session cache if necessary
     */
    public void createLocalSessionUsingDistributedApplicationPackage(long sessionId) {
        if (applicationRepo.hasLocalSession(sessionId)) {
            log.log(Level.FINE, () -> "Local session for session id " + sessionId + " already exists");
            createSessionFromId(sessionId);
            return;
        }

        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        FileReference fileReference = sessionZKClient.readApplicationPackageReference();
        log.log(Level.FINE, () -> "File reference for session id " + sessionId + ": " + fileReference);
        if (fileReference != null) {
            File rootDir = new File(Defaults.getDefaults().underVespaHome(componentRegistry.getConfigserverConfig().fileReferencesDir()));
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
            LocalSession localSession = createLocalSession(sessionDir, applicationId, sessionId);
            addLocalSession(localSession);
        }
    }

    private Optional<Long> getActiveSessionId(ApplicationId applicationId) {
        List<ApplicationId> applicationIds = applicationRepo.activeApplications();
        return applicationIds.contains(applicationId)
                ? Optional.of(applicationRepo.requireActiveSessionOf(applicationId))
                : Optional.empty();
    }

    private long getNextSessionId() {
        return new SessionCounter(componentRegistry.getConfigCurator(), tenantName).nextSessionId();
    }

    public Path getSessionPath(long sessionId) {
        return sessionsPath.append(String.valueOf(sessionId));
    }

    Path getSessionStatePath(long sessionId) {
        return getSessionPath(sessionId).append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
    }

    private SessionZooKeeperClient createSessionZooKeeperClient(long sessionId) {
        String serverId = componentRegistry.getConfigserverConfig().serverId();
        return new SessionZooKeeperClient(curator, componentRegistry.getConfigCurator(), tenantName, sessionId, serverId);
    }

    private File getAndValidateExistingSessionAppDir(long sessionId) {
        File appDir = getSessionAppDir(sessionId);
        if (!appDir.exists() || !appDir.isDirectory()) {
            throw new IllegalArgumentException("Unable to find correct application directory for session " + sessionId);
        }
        return appDir;
    }

    private File getSessionAppDir(long sessionId) {
        return new TenantFileSystemDirs(componentRegistry.getConfigServerDB(), tenantName).getUserApplicationDir(sessionId);
    }

    private void addSessionStateWatcher(long sessionId, RemoteSession remoteSession) {
        if ( ! sessionStateWatchers.containsKey(sessionId)) {
            Curator.FileCache fileCache = curator.createFileCache(getSessionStatePath(sessionId).getAbsolute(), false);
            fileCache.addListener(this::nodeChanged);
            sessionStateWatchers.put(sessionId, new SessionStateWatcher(fileCache, remoteSession, metrics, zkWatcherExecutor, this));
        }
    }

    @Override
    public String toString() {
        return getLocalSessions().toString();
    }

    /** Returns the lock for session operations for the given session id. */
    public Lock lock(long sessionId) {
        return curator.lock(lockPath(sessionId), Duration.ofMinutes(1)); // These locks shouldn't be held for very long.
    }

    public Clock clock() { return clock; }

    private Path lockPath(long sessionId) {
        return locksPath.append(String.valueOf(sessionId));
    }

    public Transaction createActivateTransaction(Session session) {
        Transaction transaction = createSetStatusTransaction(session, Session.Status.ACTIVATE);
        transaction.add(applicationRepo.createPutTransaction(session.getApplicationId(), session.getSessionId()).operations());
        return transaction;
    }

    private Transaction createSetStatusTransaction(Session session, Session.Status status) {
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
