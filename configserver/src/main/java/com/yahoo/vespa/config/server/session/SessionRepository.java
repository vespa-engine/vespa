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
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.ReloadHandler;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final long nonExistingActiveSession = 0;

    private final SessionCache<LocalSession> localSessionCache = new SessionCache<>();
    private final SessionCache<RemoteSession> remoteSessionCache = new SessionCache<>();
    private final Map<Long, SessionStateWatcher> sessionStateWatchers = new HashMap<>();
    private final Duration sessionLifetime;
    private final Clock clock;
    private final Curator curator;
    private final Executor zkWatcherExecutor;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final BooleanFlag distributeApplicationPackage;
    private final ReloadHandler reloadHandler;
    private final MetricUpdater metrics;
    private final Curator.DirectoryCache directoryCache;
    private final TenantApplications applicationRepo;
    private final SessionPreparer sessionPreparer;
    private final Path sessionsPath;
    private final TenantName tenantName;
    private final GlobalComponentRegistry componentRegistry;

    public SessionRepository(TenantName tenantName,
                             GlobalComponentRegistry componentRegistry,
                             TenantApplications applicationRepo,
                             ReloadHandler reloadHandler,
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
        this.reloadHandler = reloadHandler;
        this.metrics = componentRegistry.getMetrics().getOrCreateMetricUpdater(Metrics.createDimensions(tenantName));

        loadLocalSessions();
        initializeRemoteSessions();
        this.directoryCache = curator.createDirectoryCache(sessionsPath.getAbsolute(), false, false, componentRegistry.getZkCacheExecutor());
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
    }

    // ---------------- Local sessions ----------------------------------------------------------------

    public synchronized void addLocalSession(LocalSession session) {
        localSessionCache.addSession(session);
        long sessionId = session.getSessionId();
        Curator.FileCache fileCache = curator.createFileCache(getSessionStatePath(sessionId).getAbsolute(), false);
        RemoteSession remoteSession = createRemoteSession(sessionId);
        addSesssionStateWatcher(sessionId, fileCache, remoteSession, Optional.of(session));
    }

    public LocalSession getLocalSession(long sessionId) {
        return localSessionCache.getSession(sessionId);
    }

    public List<LocalSession> getLocalSessions() {
        return localSessionCache.getSessions();
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
                                                   Optional<ApplicationSet> currentActiveApplicationSet,
                                                   Path tenantPath,
                                                   Instant now) {
        applicationRepo.createApplication(params.getApplicationId()); // TODO jvenstad: This is wrong, but it has to be done now, since preparation can change the application ID of a session :(
        logger.log(Level.FINE, "Created application " + params.getApplicationId());
        long sessionId = session.getSessionId();
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
        Curator.CompletionWaiter waiter = sessionZooKeeperClient.createPrepareWaiter();
        ConfigChangeActions actions = sessionPreparer.prepare(applicationRepo.getHostValidator(), logger, params,
                                                              currentActiveApplicationSet, tenantPath, now,
                                                              getSessionAppDir(sessionId),
                                                              session.getApplicationPackage(), sessionZooKeeperClient);
        session.setPrepared();
        waiter.awaitCompletion(params.getTimeoutBudget().timeLeft());
        return actions;
    }

    public void deleteExpiredSessions(Map<ApplicationId, Long> activeSessions) {
        log.log(Level.FINE, "Purging old sessions");
        try {
            for (LocalSession candidate : localSessionCache.getSessions()) {
                Instant createTime = candidate.getCreateTime();
                log.log(Level.FINE, "Candidate session for deletion: " + candidate.getSessionId() + ", created: " + createTime);

                // Sessions with state other than ACTIVATED
                if (hasExpired(candidate) && !isActiveSession(candidate)) {
                    deleteLocalSession(candidate);
                } else if (createTime.plus(Duration.ofDays(1)).isBefore(clock.instant())) {
                    //  Sessions with state ACTIVATE, but which are not actually active
                    ApplicationId applicationId = candidate.getApplicationId();
                    Long activeSession = activeSessions.get(applicationId);
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
        log.log(Level.FINE, "Done purging old sessions");
    }

    private boolean hasExpired(LocalSession candidate) {
        return (candidate.getCreateTime().plus(sessionLifetime).isBefore(clock.instant()));
    }

    private boolean isActiveSession(LocalSession candidate) {
        return candidate.getStatus() == Session.Status.ACTIVATE;
    }

    public void deleteLocalSession(LocalSession session) {
        long sessionId = session.getSessionId();
        log.log(Level.FINE, "Deleting local session " + sessionId);
        SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null)  watcher.close();
        localSessionCache.removeSession(sessionId);
        NestedTransaction transaction = new NestedTransaction();
        deleteLocalSession(session, transaction);
        transaction.commit();
    }

    /** Add transactions to delete this session to the given nested transaction */
    public void deleteLocalSession(LocalSession session, NestedTransaction transaction) {
        long sessionId = session.getSessionId();
        SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(sessionId);
        transaction.add(sessionZooKeeperClient.deleteTransaction(), FileTransaction.class);
        transaction.add(FileTransaction.from(FileOperations.delete(getSessionAppDir(sessionId).getAbsolutePath())));
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
        List<LocalSession> sessions = new ArrayList<>(localSessionCache.getSessions());
        for (LocalSession session : sessions) {
            deleteLocalSession(session);
        }
    }

    // ---------------- Remote sessions ----------------------------------------------------------------

    public RemoteSession getRemoteSession(long sessionId) {
        return remoteSessionCache.getSession(sessionId);
    }

    public List<Long> getRemoteSessions() {
        return getSessionList(curator.getChildren(sessionsPath));
    }

    public void addRemoteSession(RemoteSession session) {
        remoteSessionCache.addSession(session);
        metrics.incAddedSessions();
    }

    public int deleteExpiredRemoteSessions(Clock clock, Duration expiryTime) {
        int deleted = 0;
        for (long sessionId : getRemoteSessions()) {
            RemoteSession session = remoteSessionCache.getSession(sessionId);
            if (session == null) continue; // Internal sessions not in synch with zk, continue
            if (session.getStatus() == Session.Status.ACTIVATE) continue;
            if (sessionHasExpired(session.getCreateTime(), expiryTime, clock)) {
                log.log(Level.INFO, "Remote session " + sessionId + " for " + tenantName + " has expired, deleting it");
                session.delete();
                deleted++;
            }
        }
        return deleted;
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
        for (RemoteSession session : remoteSessionCache.getSessions())
            if ( ! sessions.contains(session.getSessionId()))
                sessionRemoved(session.getSessionId());
    }

    private void checkForAddedSessions(List<Long> sessions) {
        for (Long sessionId : sessions)
            if (remoteSessionCache.getSession(sessionId) == null)
                sessionAdded(sessionId);
    }

    /**
     * A session for which we don't have a watcher, i.e. hitherto unknown to us.
     *
     * @param sessionId session id for the new session
     */
    public void sessionAdded(long sessionId) {
        log.log(Level.FINE, () -> "Adding remote session to SessionRepository: " + sessionId);
        RemoteSession remoteSession = createRemoteSession(sessionId);
        Curator.FileCache fileCache = curator.createFileCache(getSessionStatePath(sessionId).getAbsolute(), false);
        fileCache.addListener(this::nodeChanged);
        loadSessionIfActive(remoteSession);
        addRemoteSession(remoteSession);
        Optional<LocalSession> localSession = Optional.empty();
        if (distributeApplicationPackage())
            localSession = createLocalSessionUsingDistributedApplicationPackage(sessionId);
        addSesssionStateWatcher(sessionId, fileCache, remoteSession, localSession);
    }

    private boolean distributeApplicationPackage() {
        return distributeApplicationPackage.value();
    }

    private void sessionRemoved(long sessionId) {
        SessionStateWatcher watcher = sessionStateWatchers.remove(sessionId);
        if (watcher != null)  watcher.close();
        remoteSessionCache.removeSession(sessionId);
        metrics.incRemovedSessions();
    }

    private void loadSessionIfActive(RemoteSession session) {
        for (ApplicationId applicationId : applicationRepo.activeApplications()) {
            if (applicationRepo.requireActiveSessionOf(applicationId) == session.getSessionId()) {
                log.log(Level.FINE, () -> "Found active application for session " + session.getSessionId() + " , loading it");
                reloadHandler.reloadConfig(session.ensureApplicationLoaded());
                log.log(Level.INFO, session.logPre() + "Application activated successfully: " + applicationId + " (generation " + session.getSessionId() + ")");
                return;
            }
        }
    }

    private void nodeChanged() {
        zkWatcherExecutor.execute(() -> {
            Multiset<Session.Status> sessionMetrics = HashMultiset.create();
            for (RemoteSession session : remoteSessionCache.getSessions()) {
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
                    sessionsChanged();
                    synchronizeOnNew(getSessionListFromDirectoryCache(Collections.singletonList(event.getData())));
                    break;
                case CHILD_REMOVED:
                case CONNECTION_RECONNECTED:
                    sessionsChanged();
                    break;
            }
        });
    }

    private void synchronizeOnNew(List<Long> sessionList) {
        for (long sessionId : sessionList) {
            RemoteSession session = remoteSessionCache.getSession(sessionId);
            if (session == null) continue; // session might have been deleted after getting session list
            log.log(Level.FINE, () -> session.logPre() + "Confirming upload for session " + sessionId);
            session.confirmUpload();
        }
    }

    /**
     * Creates a new deployment session from an application package.
     *
     * @param applicationDirectory a File pointing to an application.
     * @param applicationId application id for this new session.
     * @param timeoutBudget Timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSession(File applicationDirectory, ApplicationId applicationId,
                                      TimeoutBudget timeoutBudget, Optional<Long> activeSessionId) {
        return create(applicationDirectory, applicationId, activeSessionId, false, timeoutBudget);
    }

    public RemoteSession createRemoteSession(long sessionId) {
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        return new RemoteSession(tenantName, sessionId, componentRegistry, sessionZKClient);
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
                                               internalRedeploy, sessionId, currentlyActiveSessionId.orElse(nonExistingActiveSession));
        return FilesApplicationPackage.fromFileWithDeployData(configApplicationDir, deployData);
    }

    private LocalSession createSessionFromApplication(ApplicationPackage applicationPackage,
                                                      long sessionId,
                                                      TimeoutBudget timeoutBudget,
                                                      Clock clock) {
        log.log(Level.FINE, TenantRepository.logPre(tenantName) + "Creating session " + sessionId + " in ZooKeeper");
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        sessionZKClient.createNewSession(clock.instant());
        Curator.CompletionWaiter waiter = sessionZKClient.getUploadWaiter();
        LocalSession session = new LocalSession(tenantName, sessionId, applicationPackage, sessionZKClient, applicationRepo);
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
            return new LocalSession(tenantName, sessionId, applicationPackage, sessionZooKeeperClient, applicationRepo);
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

    private void copyApp(File sourceDir, File destinationDir) throws IOException {
        if (destinationDir.exists())
            throw new RuntimeException("Destination dir " + destinationDir + " already exists");
        if (! sourceDir.isDirectory())
            throw new IllegalArgumentException(sourceDir.getAbsolutePath() + " is not a directory");

        // Copy app atomically: Copy to a temp dir and move to destination
        java.nio.file.Path tempDestinationDir = Files.createTempDirectory(destinationDir.getParentFile().toPath(), "app-package");
        log.log(Level.FINE, "Copying dir " + sourceDir.getAbsolutePath() + " to " + tempDestinationDir.toFile().getAbsolutePath());
        IOUtils.copyDirectory(sourceDir, tempDestinationDir.toFile());
        log.log(Level.FINE, "Moving " + tempDestinationDir + " to " + destinationDir.getAbsolutePath());
        Files.move(tempDestinationDir, destinationDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Returns a new session instance for the given session id.
     */
    LocalSession createSessionFromId(long sessionId) {
        File sessionDir = getAndValidateExistingSessionAppDir(sessionId);
        ApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(sessionDir);
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        return new LocalSession(tenantName, sessionId, applicationPackage, sessionZKClient, applicationRepo);
    }

    /**
     * Returns a new local session for the given session id if it does not already exist.
     * Will also add the session to the local session cache if necessary
     */
    public Optional<LocalSession> createLocalSessionUsingDistributedApplicationPackage(long sessionId) {
        if (applicationRepo.hasLocalSession(sessionId)) {
            log.log(Level.FINE, "Local session for session id " + sessionId + " already exists");
            return Optional.of(createSessionFromId(sessionId));
        }

        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionId);
        FileReference fileReference = sessionZKClient.readApplicationPackageReference();
        log.log(Level.FINE, "File reference for session id " + sessionId + ": " + fileReference);
        if (fileReference != null) {
            File rootDir = new File(Defaults.getDefaults().underVespaHome(componentRegistry.getConfigserverConfig().fileReferencesDir()));
            File sessionDir;
            FileDirectory fileDirectory = new FileDirectory(rootDir);
            try {
                sessionDir = fileDirectory.getFile(fileReference);
            } catch (IllegalArgumentException e) {
                // We cannot be guaranteed that the file reference exists (it could be that it has not
                // been downloaded yet), and e.g when bootstrapping we cannot throw an exception in that case
                log.log(Level.INFO, "File reference for session id " + sessionId + ": " + fileReference + " not found in " + fileDirectory);
                return Optional.empty();
            }
            ApplicationId applicationId = sessionZKClient.readApplicationId();
            log.log(Level.INFO, "Creating local session for session id " + sessionId);
            LocalSession localSession = createLocalSession(sessionDir, applicationId, sessionId);
            addLocalSession(localSession);
            return Optional.of(localSession);
        }
        return Optional.empty();
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
        Optional<NodeFlavors> nodeFlavors = componentRegistry.getZone().nodeFlavors();
        Path sessionPath = getSessionPath(sessionId);
        return new SessionZooKeeperClient(curator, componentRegistry.getConfigCurator(), sessionPath, serverId, nodeFlavors);
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

    private void addSesssionStateWatcher(long sessionId, Curator.FileCache fileCache, RemoteSession remoteSession, Optional<LocalSession> localSession) {
        // Remote session will always be present in an existing state watcher, but local session might not
        if (sessionStateWatchers.containsKey(sessionId)) {
            localSession.ifPresent(session -> sessionStateWatchers.get(sessionId).addLocalSession(session));
        } else {
            sessionStateWatchers.put(sessionId, new SessionStateWatcher(fileCache, reloadHandler, remoteSession,
                                                                        localSession, metrics, zkWatcherExecutor, this));
        }
    }

    @Override
    public String toString() {
        return getLocalSessions().toString();
    }

    public ReloadHandler getReloadHandler() { return reloadHandler; }

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
