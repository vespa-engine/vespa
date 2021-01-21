// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.ReloadListener;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toSet;

/**
 * The applications of a tenant.
 *
 * @author Ulf Lilleengen
 * @author jonmv
 */
public class TenantApplications implements RequestHandler, HostValidator<ApplicationId> {

    private static final Logger log = Logger.getLogger(TenantApplications.class.getName());

    private final ApplicationCuratorDatabase database;
    private final Curator.DirectoryCache directoryCache;
    private final Executor zkWatcherExecutor;
    private final Metrics metrics;
    private final TenantName tenant;
    private final ReloadListener reloadListener;
    private final ConfigResponseFactory responseFactory;
    private final HostRegistry hostRegistry;
    private final ApplicationMapper applicationMapper = new ApplicationMapper();
    private final MetricUpdater tenantMetricUpdater;
    private final Clock clock;
    private final TenantFileSystemDirs tenantFileSystemDirs;

    public TenantApplications(TenantName tenant, Curator curator, StripedExecutor<TenantName> zkWatcherExecutor,
                              ExecutorService zkCacheExecutor, Metrics metrics, ReloadListener reloadListener,
                              ConfigserverConfig configserverConfig, HostRegistry hostRegistry,
                              TenantFileSystemDirs tenantFileSystemDirs, Clock clock) {
        this.database = new ApplicationCuratorDatabase(tenant, curator);
        this.tenant = tenant;
        this.zkWatcherExecutor = command -> zkWatcherExecutor.execute(tenant, command);
        this.directoryCache = database.createApplicationsPathCache(zkCacheExecutor);
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
        this.metrics = metrics;
        this.reloadListener = reloadListener;
        this.responseFactory = ConfigResponseFactory.create(configserverConfig);
        this.tenantMetricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(tenant));
        this.hostRegistry = hostRegistry;
        this.tenantFileSystemDirs = tenantFileSystemDirs;
        this.clock = clock;
    }

    // For testing only
    public static TenantApplications create(HostRegistry hostRegistry,
                                            TenantName tenantName,
                                            Curator curator,
                                            ConfigserverConfig configserverConfig,
                                            Clock clock,
                                            ReloadListener reloadListener) {
        return new TenantApplications(tenantName,
                                      curator,
                                      new StripedExecutor<>(new InThreadExecutorService()),
                                      new InThreadExecutorService(),
                                      Metrics.createTestMetrics(),
                                      reloadListener,
                                      configserverConfig,
                                      hostRegistry,
                                      new TenantFileSystemDirs(new ConfigServerDB(configserverConfig), tenantName),
                                      clock);
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
                    log.log(Level.FINE, TenantRepository.logPre(applicationId) + "Application added: " + applicationId);
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
            // We may have lost events and may need to remove applications.
            removeUnusedApplications();
        });
    }

    /**
     * Gets a config for the given app, or null if not found
     */
    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        Application application = getApplication(appId, vespaVersion);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, TenantRepository.logPre(appId) + "Resolving for tenant '" + tenant + "' with handler for application '" + application + "'");
        }
        return application.resolveConfig(req, responseFactory);
    }

    private void notifyReloadListeners(ApplicationSet applicationSet) {
        if (applicationSet.getAllApplications().isEmpty()) throw new IllegalArgumentException("application set cannot be empty");

        reloadListener.hostsUpdated(applicationSet.getAllApplications().get(0).toApplicationInfo().getApplicationId(),
                                    applicationSet.getAllHosts());
        reloadListener.configActivated(applicationSet);
    }

    /**
     * Activates the config of the given app. Notifies listeners
     *
     * @param applicationSet the {@link ApplicationSet} to be reloaded
     */
    public void activateApplication(ApplicationSet applicationSet, long activeSessionId) {
        ApplicationId id = applicationSet.getId();
        try (Lock lock = lock(id)) {
            if ( ! exists(id))
                return; // Application was deleted before activation.
            if (applicationSet.getApplicationGeneration() != activeSessionId)
                return; // Application activated a new session before we got here.

            setLiveApp(applicationSet);
            notifyReloadListeners(applicationSet);
        }
    }

    public void removeApplication(ApplicationId applicationId) {
        try (Lock lock = lock(applicationId)) {
            if (exists(applicationId)) {
                log.log(Level.INFO, "Tried removing application " + applicationId + ", but it seems to have been deployed again");
                return;
            }

            if (applicationMapper.hasApplication(applicationId, clock.instant())) {
                applicationMapper.remove(applicationId);
                hostRegistry.removeHostsForKey(applicationId);
                reloadListenersOnRemove(applicationId);
                tenantMetricUpdater.setApplications(applicationMapper.numApplications());
                metrics.removeMetricUpdater(Metrics.createDimensions(applicationId));
                log.log(Level.INFO, "Application removed: " + applicationId);
            }
        }
    }

    public void removeApplicationsExcept(Set<ApplicationId> applications) {
        for (ApplicationId activeApplication : applicationMapper.listApplicationIds()) {
            if ( ! applications.contains(activeApplication)) {
                removeApplication(activeApplication);
            }
        }
    }

    private void reloadListenersOnRemove(ApplicationId applicationId) {
        reloadListener.hostsUpdated(applicationId, hostRegistry.getHostsForKey(applicationId));
        reloadListener.applicationRemoved(applicationId);
    }

    private void setLiveApp(ApplicationSet applicationSet) {
        ApplicationId id = applicationSet.getId();
        Collection<String> hostsForApp = applicationSet.getAllHosts();
        hostRegistry.update(id, hostsForApp);
        applicationSet.updateHostMetrics();
        tenantMetricUpdater.setApplications(applicationMapper.numApplications());
        applicationMapper.register(id, applicationSet);
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
     * Given baseIdSegment search/ and id search/qrservers/default.0, return search/qrservers
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
        return hostRegistry.getKeyForHost(hostName);
    }

    @Override
    public Set<FileReference> listFileReferences(ApplicationId applicationId) {
        return applicationMapper.listApplications(applicationId).stream()
                .flatMap(app -> app.getModel().fileReferences().stream())
                .collect(toSet());
    }

    @Override
    public void verifyHosts(ApplicationId applicationId, Collection<String> newHosts) {
        hostRegistry.verifyHosts(applicationId, newHosts);
        reloadListener.verifyHostsAreAvailable(applicationId, newHosts);
    }

    public HostValidator<ApplicationId> getHostValidator() {
        return this;
    }

    public ApplicationId getApplicationIdForHostName(String hostname) {
        return hostRegistry.getKeyForHost(hostname);
    }

    public TenantFileSystemDirs getTenantFileSystemDirs() { return tenantFileSystemDirs; }

}
