// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.VersionCompatibility;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.ConfigActivationListener;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.CompletionTimeoutException;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.curator.Curator.CompletionWaiter;
import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static java.util.stream.Collectors.toSet;

/**
 * The applications of a tenant.
 *
 * @author Ulf Lilleengen
 * @author jonmv
 */
public class TenantApplications implements RequestHandler, HostValidator {

    private static final Logger log = Logger.getLogger(TenantApplications.class.getName());

    private final Curator curator;
    private final ApplicationCuratorDatabase database;
    private final Curator.DirectoryCache directoryCache;
    private final Executor zkWatcherExecutor;
    private final Metrics metrics;
    private final TenantName tenant;
    private final ConfigActivationListener configActivationListener;
    private final ConfigResponseFactory responseFactory;
    private final HostRegistry hostRegistry;
    private final ApplicationMapper applicationMapper = new ApplicationMapper();
    private final MetricUpdater tenantMetricUpdater;
    private final Clock clock;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final String serverId;
    private final ListFlag<String> incompatibleVersions;

    public TenantApplications(TenantName tenant, Curator curator, StripedExecutor<TenantName> zkWatcherExecutor,
                              ExecutorService zkCacheExecutor, Metrics metrics, ConfigActivationListener configActivationListener,
                              ConfigserverConfig configserverConfig, HostRegistry hostRegistry,
                              TenantFileSystemDirs tenantFileSystemDirs, Clock clock, FlagSource flagSource) {
        this.curator = curator;
        this.database = new ApplicationCuratorDatabase(tenant, curator);
        this.tenant = tenant;
        this.zkWatcherExecutor = command -> zkWatcherExecutor.execute(tenant, command);
        this.directoryCache = database.createApplicationsPathCache(zkCacheExecutor);
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
        this.metrics = metrics;
        this.configActivationListener = configActivationListener;
        this.responseFactory = ConfigResponseFactory.create(configserverConfig);
        this.tenantMetricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(tenant));
        this.hostRegistry = hostRegistry;
        this.tenantFileSystemDirs = tenantFileSystemDirs;
        this.clock = clock;
        this.serverId = configserverConfig.serverId();
        this.incompatibleVersions = PermanentFlags.INCOMPATIBLE_VERSIONS.bindTo(flagSource);
    }

    /** The curator backed ZK storage of this. */
    public ApplicationCuratorDatabase database() { return database; }

    /**
     * List the active applications of a tenant in this config server.
     *
     * @return a list of {@link ApplicationId}s that are active.
     */
    public List<ApplicationId> activeApplications() {
        return database().activeApplications();
    }

    public boolean exists(ApplicationId id) {
        return database().exists(id);
    }

    /**
     * Returns the active session id for the given application.
     * Returns Optional.empty if application not found or no active session exists.
     */
    public Optional<Long> activeSessionOf(ApplicationId id) {
        return database().activeSessionOf(id);
    }

    public boolean sessionExistsInFileSystem(long sessionId) {
        return Files.exists(Paths.get(tenantFileSystemDirs.sessionsPath().getAbsolutePath(), String.valueOf(sessionId)));
    }

    /**
     * Returns a transaction which writes the given session id as the currently active for the given application.
     *
     * @param applicationId An {@link ApplicationId} that represents an active application.
     * @param sessionId Id of the session containing the application package for this id.
     */
    public Transaction createPutTransaction(ApplicationId applicationId, long sessionId) {
        return database().createPutTransaction(applicationId, sessionId);
    }

    /**
     * Creates a node for the given application, marking its existence.
     */
    public void createApplication(ApplicationId id) {
        database().createApplication(id);
    }

    /**
     * Return the active session id for a given application.
     *
     * @param  applicationId an {@link ApplicationId}
     * @return session id of given application id.
     * @throws IllegalArgumentException if the application does not exist
     */
    public long requireActiveSessionOf(ApplicationId applicationId) {
        return activeSessionOf(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application '" + applicationId + "' has no active session."));
    }

    /**
     * Returns a transaction which deletes this application.
     */
    public CuratorTransaction createDeleteTransaction(ApplicationId applicationId) {
        return database().createDeleteTransaction(applicationId);
    }

    /**
     * Removes all applications not known to this from the config server state.
     */
    public void removeUnusedApplications() {
        removeApplicationsExcept(Set.copyOf(activeApplications()));
    }

    /**
     * Closes the application repo. Once a repo has been closed, it should not be used again.
     */
    public void close() {
        directoryCache.close();
    }

    /** Returns the lock for changing the session status of the given application. */
    public Lock lock(ApplicationId id) {
        return database().lock(id);
    }

    private void childEvent(CuratorFramework ignored, PathChildrenCacheEvent event) {
        zkWatcherExecutor.execute(() -> {
            // Note: event.getData() might return null on types not handled here (CONNECTION_*, INITIALIZED, see javadoc)
            switch (event.getType()) {
                case CHILD_ADDED:
                    /* A new application is added when a session is added, @see
                    {@link com.yahoo.vespa.config.server.session.SessionRepository#childEvent(CuratorFramework, PathChildrenCacheEvent)} */
                    ApplicationId applicationId = ApplicationId.fromSerializedForm(Path.fromString(event.getData().getPath()).getName());
                    log.log(Level.FINE, () -> TenantRepository.logPre(applicationId) + "Application added: " + applicationId);
                    break;
                // Event CHILD_REMOVED will be triggered on all config servers if deleteApplication() above is called on one of them
                case CHILD_REMOVED:
                    removeApplication(ApplicationId.fromSerializedForm(Path.fromString(event.getData().getPath()).getName()));
                    break;
                case CHILD_UPDATED:
                    // do nothing, application just got redeployed
                    break;
                default:
                    break;
            }
        });
    }

    /**
     * Gets a config for the given app, or null if not found
     */
    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        Application application = getApplication(appId, vespaVersion);
        log.log(Level.FINE, () -> TenantRepository.logPre(appId) + "Resolving config");
        return application.resolveConfig(req, responseFactory);
    }

    private void notifyConfigActivationListeners(ApplicationSet applicationSet) {
        List<Application> applications = applicationSet.getAllApplications();
        if (applications.isEmpty()) throw new IllegalArgumentException("application set cannot be empty");

        hostRegistry.update(applications.get(0).getId(), applicationSet.getAllHosts());
        configActivationListener.configActivated(applicationSet);
    }

    /**
     * Activates the config of the given app. Notifies listeners
     *
     * @param applicationSet the {@link ApplicationSet} to be activated
     */
    public void activateApplication(ApplicationSet applicationSet, long activeSessionId) {
        ApplicationId id = applicationSet.getId();
        try (@SuppressWarnings("unused") Lock lock = lock(id)) {
            if ( ! exists(id))
                return; // Application was deleted before activation.
            if (applicationSet.getApplicationGeneration() != activeSessionId)
                return; // Application activated a new session before we got here.

            setActiveApp(applicationSet);
            notifyConfigActivationListeners(applicationSet);
        }
    }

    // Note: Assumes that caller already holds the application lock
    // (when getting event from zookeeper to remove application,
    // the lock should be held by the thread that causes the event to happen)
    public void removeApplication(ApplicationId applicationId) {
        log.log(Level.FINE, () -> "Removing application " + applicationId);
        if (exists(applicationId)) {
            log.log(Level.INFO, "Tried removing application " + applicationId + ", but it seems to have been deployed again");
            return;
        }

        if (hasApplication(applicationId)) {
            applicationMapper.remove(applicationId);
            hostRegistry.removeHosts(applicationId);
            configActivationListenersOnRemove(applicationId);
            tenantMetricUpdater.setApplications(applicationMapper.numApplications());
            metrics.removeMetricUpdater(Metrics.createDimensions(applicationId));
            getRemoveApplicationWaiter(applicationId).notifyCompletion();
            log.log(Level.INFO, "Application removed: " + applicationId);
        }
    }

    public boolean hasApplication(ApplicationId applicationId) {
        return applicationMapper.hasApplication(applicationId, clock.instant());
    }

    public void removeApplicationsExcept(Set<ApplicationId> applications) {
        for (ApplicationId activeApplication : applicationMapper.listApplicationIds()) {
            if ( ! applications.contains(activeApplication)) {
                try (@SuppressWarnings("unused") var applicationLock = lock(activeApplication)){
                    removeApplication(activeApplication);
                }
            }
        }
    }

    private void configActivationListenersOnRemove(ApplicationId applicationId) {
        hostRegistry.removeHosts(applicationId);
        configActivationListener.applicationRemoved(applicationId);
    }

    private void setActiveApp(ApplicationSet applicationSet) {
        ApplicationId applicationId = applicationSet.getId();
        Collection<String> hostsForApp = applicationSet.getAllHosts();
        hostRegistry.update(applicationId, hostsForApp);
        applicationSet.updateHostMetrics();
        tenantMetricUpdater.setApplications(applicationMapper.numApplications());
        applicationMapper.register(applicationId, applicationSet);
    }

    @Override
    public Set<ConfigKey<?>> listNamedConfigs(ApplicationId appId, Optional<Version> vespaVersion, ConfigKey<?> keyToMatch, boolean recursive) {
        Application application = getApplication(appId, vespaVersion);
        return listConfigs(application, keyToMatch, recursive);
    }

    private Set<ConfigKey<?>> listConfigs(Application application, ConfigKey<?> keyToMatch, boolean recursive) {
        Set<ConfigKey<?>> ret = new LinkedHashSet<>();
        for (ConfigKey<?> key : application.allConfigsProduced()) {
            String configId = key.getConfigId();
            if (recursive) {
                key = new ConfigKey<>(key.getName(), configId, key.getNamespace());
            } else {
                // Include first part of id as id
                key = new ConfigKey<>(key.getName(), configId.split("/")[0], key.getNamespace());
            }
            if (keyToMatch != null) {
                String n = key.getName(); // Never null
                String ns = key.getNamespace(); // Never null
                if (n.equals(keyToMatch.getName()) &&
                    ns.equals(keyToMatch.getNamespace()) &&
                    configId.startsWith(keyToMatch.getConfigId()) &&
                    !(configId.equals(keyToMatch.getConfigId()))) {

                    if (!recursive) {
                        // For non-recursive, include the id segment we were searching for, and first part of the rest
                        key = new ConfigKey<>(key.getName(), appendOneLevelOfId(keyToMatch.getConfigId(), configId), key.getNamespace());
                    }
                    ret.add(key);
                }
            } else {
                ret.add(key);
            }
        }
        return ret;
    }

    @Override
    public Set<ConfigKey<?>> listConfigs(ApplicationId appId, Optional<Version> vespaVersion, boolean recursive) {
        Application application = getApplication(appId, vespaVersion);
        return listConfigs(application, null, recursive);
    }

    /**
     * Given baseIdSegment search/ and id search/container/default.0, return search/container
     * @return id segment with one extra level from the id appended
     */
    String appendOneLevelOfId(String baseIdSegment, String id) {
        if ("".equals(baseIdSegment)) return id.split("/")[0];
        String theRest = id.substring(baseIdSegment.length());
        if ("".equals(theRest)) return id;
        theRest = theRest.replaceFirst("/", "");
        String theRestFirstSeg = theRest.split("/")[0];
        return baseIdSegment+"/"+theRestFirstSeg;
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced(ApplicationId appId, Optional<Version> vespaVersion) {
        Application application = getApplication(appId, vespaVersion);
        return application.allConfigsProduced();
    }

    private Application getApplication(ApplicationId appId, Optional<Version> vespaVersion) {
        try {
            return applicationMapper.getForVersion(appId, vespaVersion, clock.instant());
        } catch (VersionDoesNotExistException ex) {
            throw new NotFoundException(String.format("%sNo such application (id %s): %s", TenantRepository.logPre(tenant), appId, ex.getMessage()));
        }
    }

    @Override
    public Set<String> allConfigIds(ApplicationId appId, Optional<Version> vespaVersion) {
        Application application = getApplication(appId, vespaVersion);
        return application.allConfigIds();
    }

    @Override
    public boolean hasApplication(ApplicationId appId, Optional<Version> vespaVersion) {
        return hasHandler(appId, vespaVersion);
    }

    private boolean hasHandler(ApplicationId appId, Optional<Version> vespaVersion) {
        return applicationMapper.hasApplicationForVersion(appId, vespaVersion, clock.instant());
    }

    @Override
    public ApplicationId resolveApplicationId(String hostName) {
        return hostRegistry.getApplicationId(hostName);
    }

    @Override
    public Set<FileReference> listFileReferences(ApplicationId applicationId) {
        return applicationMapper.listApplications(applicationId).stream()
                .flatMap(app -> app.getModel().fileReferences().stream())
                .collect(toSet());
    }

    @Override
    public boolean compatibleWith(Optional<Version> vespaVersion, ApplicationId application) {
        if (vespaVersion.isEmpty()) return true;
        Version wantedVersion = applicationMapper.getForVersion(application, Optional.empty(), clock.instant())
                                                 .getModel().wantedNodeVersion();
        return VersionCompatibility.fromVersionList(incompatibleVersions.with(APPLICATION_ID, application.serializedForm()).value())
                                   .accept(vespaVersion.get(), wantedVersion);
    }

    @Override
    public void verifyHosts(ApplicationId applicationId, Collection<String> newHosts) {
        hostRegistry.verifyHosts(applicationId, newHosts);
    }

    public TenantFileSystemDirs getTenantFileSystemDirs() { return tenantFileSystemDirs; }

    public CompletionWaiter createRemoveApplicationWaiter(ApplicationId applicationId) {
        return RemoveApplicationWaiter.createAndInitialize(curator, applicationId, serverId);
    }

    public CompletionWaiter getRemoveApplicationWaiter(ApplicationId applicationId) {
        return RemoveApplicationWaiter.create(curator, applicationId, serverId);
    }

    /**
     * Waiter for removing application. Will wait for some time for all servers to remove application,
     * but will accept the majority of servers to have removed app if it takes a long time.
     */
    // TODO: Merge with CuratorCompletionWaiter
    static class RemoveApplicationWaiter implements CompletionWaiter {

        private static final java.util.logging.Logger log = Logger.getLogger(RemoveApplicationWaiter.class.getName());
        private static final Duration waitForAllDefault = Duration.ofSeconds(5);

        private final Curator curator;
        private final Path barrierPath;
        private final Path waiterNode;
        private final Duration waitForAll;
        private final Clock clock = Clock.systemUTC();

        RemoveApplicationWaiter(Curator curator, ApplicationId applicationId, String serverId) {
            this(curator, applicationId, serverId, waitForAllDefault);
        }

        RemoveApplicationWaiter(Curator curator, ApplicationId applicationId, String serverId, Duration waitForAll) {
            this.barrierPath = TenantRepository.getBarriersPath().append(applicationId.tenant().value())
                                               .append("delete-application")
                                               .append(applicationId.serializedForm());
            this.waiterNode = barrierPath.append(serverId);
            this.curator = curator;
            this.waitForAll = waitForAll;
        }

        @Override
        public void awaitCompletion(Duration timeout) {
            List<String> respondents;
            try {
                respondents = awaitInternal(timeout);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (respondents.size() < barrierMemberCount()) {
                throw new CompletionTimeoutException("Timed out waiting for peer config servers to remove application " +
                                                     "(waited for barrier " + barrierPath + ")." +
                                                     "Got response from " + respondents + ", but need response from " +
                                                     "at least " + barrierMemberCount() + " server(s). " +
                                                     "Timeout passed as argument was " + timeout.toMillis() + " ms");
            }
        }

        private List<String> awaitInternal(Duration timeout) throws Exception {
            Instant startTime = clock.instant();
            Instant endTime = startTime.plus(timeout);
            Instant gotQuorumTime = Instant.EPOCH;
            List<String> respondents;
            do {
                respondents = curator.framework().getChildren().forPath(barrierPath.getAbsolute());
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, respondents.size() + "/" + curator.zooKeeperEnsembleCount() + " responded: " +
                                        respondents + ", all participants: " + curator.zooKeeperEnsembleConnectionSpec());
                }

                // If all config servers responded, return
                if (respondents.size() == curator.zooKeeperEnsembleCount()) {
                    logBarrierCompleted(respondents, startTime);
                    break;
                }

                // If some are missing, quorum is enough, but wait for all up to 5 seconds before returning
                if (respondents.size() >= barrierMemberCount()) {
                    if (gotQuorumTime.isBefore(startTime))
                        gotQuorumTime = clock.instant();

                    // Give up if more than some time has passed since we got quorum, otherwise continue
                    if (Duration.between(clock.instant(), gotQuorumTime.plus(waitForAll)).isNegative()) {
                        logBarrierCompleted(respondents, startTime);
                        break;
                    }
                }

                Thread.sleep(100);
            } while (clock.instant().isBefore(endTime));

            return respondents;
        }

        private void logBarrierCompleted(List<String> respondents, Instant startTime) {
            Duration duration = Duration.between(startTime, Instant.now());
            Level level = (duration.minus(Duration.ofSeconds(5))).isNegative() ? Level.FINE : Level.INFO;
            log.log(level, () -> barrierCompletedMessage(respondents, duration));
        }

        private String barrierCompletedMessage(List<String> respondents, Duration duration) {
            return barrierPath + " completed in " + duration.toString() +
                   ", " + respondents.size() + "/" + curator.zooKeeperEnsembleCount() + " responded: " + respondents;
        }

        @Override
        public void notifyCompletion() {
            try {
                curator.framework().create().forPath(waiterNode.getAbsolute());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() { return "'" + barrierPath + "', " + barrierMemberCount() + " members"; }

        public static CompletionWaiter create(Curator curator, ApplicationId applicationId, String serverId) {
            return new RemoveApplicationWaiter(curator, applicationId, serverId);
        }

        public static CompletionWaiter create(Curator curator, ApplicationId applicationId, String serverId, Duration waitForAll) {
            return new RemoveApplicationWaiter(curator, applicationId, serverId, waitForAll);
        }

        public static CompletionWaiter createAndInitialize(Curator curator, ApplicationId applicationId, String serverId) {
            return createAndInitialize(curator, applicationId, serverId, waitForAllDefault);
        }

        public static CompletionWaiter createAndInitialize(Curator curator, ApplicationId applicationId, String serverId, Duration waitForAll) {
            RemoveApplicationWaiter waiter = new RemoveApplicationWaiter(curator, applicationId, serverId, waitForAll);

            // Cleanup and create a new barrier path
            Path barrierPath = waiter.barrierPath();
            curator.delete(barrierPath);
            curator.create(barrierPath.getParentPath());
            curator.createAtomically(barrierPath);

            return waiter;
        }

        private int barrierMemberCount() { return (curator.zooKeeperEnsembleCount() / 2) + 1; /* majority */ }

        private Path barrierPath() { return barrierPath; }

    }

}
