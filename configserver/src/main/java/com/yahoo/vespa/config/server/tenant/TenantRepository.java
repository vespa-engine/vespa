// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.google.common.collect.ImmutableSet;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.Lock;
import com.yahoo.concurrent.Locks;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.ConfigActivationListener;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.SessionPreparer;
import com.yahoo.vespa.config.server.session.SessionRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.flags.FlagSource;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This component will monitor the set of tenants in the config server by watching in ZooKeeper.
 * It will set up Tenant objects accordingly, which will manage the config sessions per tenant.
 * This class will read the preexisting set of tenants from ZooKeeper at startup. (For now it will also
 * create a default tenant since that will be used for APIs that do no know about tenants or have not yet
 * implemented support for it).
 *
 * This instance is called from two different threads, the http handler threads and the zookeeper watcher threads.
 * To create or delete a tenant, the handler calls {@link TenantRepository#addTenant} and {@link TenantRepository#deleteTenant} methods.
 * This will delete shared state from zookeeper, and return, so it does not mean a tenant is immediately deleted.
 *
 * Once a tenant is deleted from zookeeper, the zookeeper watcher thread will get notified on all config servers, and
 * shutdown and delete any per-configserver state.
 *
 * @author Vegard Havdal
 * @author Ulf Lilleengen
 */
public class TenantRepository {

    public static final TenantName HOSTED_VESPA_TENANT = TenantName.from("hosted-vespa");
    private static final TenantName DEFAULT_TENANT = TenantName.defaultName();

    private static final Path tenantsPath = Path.fromString("/config/v2/tenants/");
    private static final Path locksPath = Path.fromString("/config/v2/locks/");
    private static final Path barriersPath = Path.fromString("/config/v2/barriers/");
    private static final Path vespaPath = Path.fromString("/vespa");
    private static final Duration checkForRemovedApplicationsInterval = Duration.ofMinutes(1);
    private static final Logger log = Logger.getLogger(TenantRepository.class.getName());

    private final Map<TenantName, Tenant> tenants = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Locks<TenantName> tenantLocks = new Locks<>(1, TimeUnit.MINUTES);
    private final HostRegistry hostRegistry;
    private final TenantListener tenantListener;
    private final Curator curator;
    private final Metrics metrics;
    private final MetricUpdater metricUpdater;
    private final ExecutorService zkCacheExecutor;
    private final StripedExecutor<TenantName> zkSessionWatcherExecutor;
    private final StripedExecutor<TenantName> zkApplicationWatcherExecutor;
    private final FileDistributionFactory fileDistributionFactory;
    private final ExecutorService deployHelperExecutor;
    private final FlagSource flagSource;
    private final SecretStore secretStore;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final ConfigserverConfig configserverConfig;
    private final ConfigServerDB configServerDB;
    private final Zone zone;
    private final Clock clock;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final ConfigActivationListener configActivationListener;
    private final ScheduledExecutorService checkForRemovedApplicationsService =
            new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("check for removed applications"));
    private final Curator.DirectoryCache directoryCache;
    private final ZookeeperServerConfig zookeeperServerConfig;

    /**
     * Creates a new tenant repository
     */
    @Inject
    public TenantRepository(HostRegistry hostRegistry,
                            Curator curator,
                            Metrics metrics,
                            FlagSource flagSource,
                            SecretStore secretStore,
                            HostProvisionerProvider hostProvisionerProvider,
                            ConfigserverConfig configserverConfig,
                            ConfigServerDB configServerDB,
                            Zone zone,
                            ModelFactoryRegistry modelFactoryRegistry,
                            ConfigDefinitionRepo configDefinitionRepo,
                            ConfigActivationListener configActivationListener,
                            TenantListener tenantListener,
                            ZookeeperServerConfig zookeeperServerConfig,
                            FileDirectory fileDirectory) {
        this(hostRegistry,
             curator,
             metrics,
             new StripedExecutor<>(),
             new StripedExecutor<>(),
             new FileDistributionFactory(configserverConfig, fileDirectory),
             flagSource,
             Executors.newFixedThreadPool(1, ThreadFactoryFactory.getThreadFactory(TenantRepository.class.getName())),
             secretStore,
             hostProvisionerProvider,
             configserverConfig,
             configServerDB,
             zone,
             Clock.systemUTC(),
             modelFactoryRegistry,
             configDefinitionRepo,
             configActivationListener,
             tenantListener,
             zookeeperServerConfig);
    }

    public TenantRepository(HostRegistry hostRegistry,
                            Curator curator,
                            Metrics metrics,
                            StripedExecutor<TenantName> zkApplicationWatcherExecutor ,
                            StripedExecutor<TenantName> zkSessionWatcherExecutor,
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
                            ConfigActivationListener configActivationListener,
                            TenantListener tenantListener,
                            ZookeeperServerConfig zookeeperServerConfig) {
        this.hostRegistry = hostRegistry;
        this.configserverConfig = configserverConfig;
        this.curator = curator;
        this.metrics = metrics;
        metricUpdater = metrics.getOrCreateMetricUpdater(Collections.emptyMap());
        this.zkCacheExecutor = zkCacheExecutor;
        this.zkApplicationWatcherExecutor = zkApplicationWatcherExecutor;
        this.zkSessionWatcherExecutor = zkSessionWatcherExecutor;
        this.fileDistributionFactory = fileDistributionFactory;
        this.flagSource = flagSource;
        this.secretStore = secretStore;
        this.hostProvisionerProvider = hostProvisionerProvider;
        this.configServerDB = configServerDB;
        this.zone = zone;
        this.clock = clock;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.configDefinitionRepo = configDefinitionRepo;
        this.configActivationListener = configActivationListener;
        this.tenantListener = tenantListener;
        this.zookeeperServerConfig = zookeeperServerConfig;
        // This we should control with a feature flag.
        this.deployHelperExecutor = createModelBuilderExecutor();

        curator.framework().getConnectionStateListenable().addListener(this::stateChanged);

        createPaths();
        createSystemTenants(configserverConfig);

        this.directoryCache = curator.createDirectoryCache(tenantsPath.getAbsolute(), false, false, zkCacheExecutor);
        this.directoryCache.addListener(this::childEvent);
        this.directoryCache.start();
        bootstrapTenants();
        notifyTenantsLoaded();
        checkForRemovedApplicationsService.scheduleWithFixedDelay(this::removeUnusedApplications,
                                                                  checkForRemovedApplicationsInterval.getSeconds(),
                                                                  checkForRemovedApplicationsInterval.getSeconds(),
                                                                  TimeUnit.SECONDS);
    }

    private ExecutorService createModelBuilderExecutor() {
        final long GB = 1024*1024*1024;
        long maxHeap = Runtime.getRuntime().maxMemory();
        int maxThreadsToFitInMemory = (int)((maxHeap + (GB - 1))/(1*GB));
        int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), maxThreadsToFitInMemory);
        return Executors.newFixedThreadPool(numThreads, ThreadFactoryFactory.getDaemonThreadFactory("deploy-helper"));
    }

    private void notifyTenantsLoaded() {
        tenantListener.onTenantsLoaded();
    }

    public Tenant addTenant(TenantName tenantName) {
        try (Lock lock = tenantLocks.lock(tenantName)) {
            writeTenantPath(tenantName);
            return createTenant(tenantName, clock.instant());
        }
    }

    public void createAndWriteTenantMetaData(Tenant tenant) {
        createWriteTenantMetaDataTransaction(createMetaData(tenant)).commit();
    }

    public Transaction createWriteTenantMetaDataTransaction(TenantMetaData tenantMetaData) {
        return new CuratorTransaction(curator).add(
                CuratorOperations.setData(TenantRepository.getTenantPath(tenantMetaData.tenantName()).getAbsolute(),
                                          tenantMetaData.asJsonBytes()));
    }

    private TenantMetaData createMetaData(Tenant tenant) {
        Instant deployTime = tenant.getSessionRepository().clock().instant();
        Instant createdTime = getTenantMetaData(tenant).createdTimestamp();
        if (createdTime.equals(Instant.EPOCH))
            createdTime = deployTime;
        return new TenantMetaData(tenant.getName(), deployTime, createdTime);
    }

    public TenantMetaData getTenantMetaData(Tenant tenant) {
        Optional<byte[]> data = getCurator().getData(TenantRepository.getTenantPath(tenant.getName()));
        Optional<TenantMetaData> metaData;
        try {
            metaData = data.map(bytes -> TenantMetaData.fromJsonString(tenant.getName(), Utf8.toString(bytes)));
        } catch (IllegalArgumentException e) {
            // If no data or illegal data
            metaData = Optional.empty();
        }
        return metaData.orElse(new TenantMetaData(tenant.getName(), tenant.getCreatedTime(), tenant.getCreatedTime()));
    }

    private static Set<TenantName> readTenantsFromZooKeeper(Curator curator) {
        return curator.getChildren(tenantsPath).stream().map(TenantName::from).collect(Collectors.toSet());
    }

    private void createPaths() {
        curator.create(tenantsPath);
        curator.create(locksPath);
        curator.create(barriersPath);
        curator.create(vespaPath);
    }

    private void bootstrapTenants() {
        ExecutorService bootstrapExecutor = Executors.newFixedThreadPool(configserverConfig.numParallelTenantLoaders(),
                new DaemonThreadFactory("bootstrap-tenant-"));
        // Keep track of tenants created
        Map<TenantName, Future<?>> futures = new HashMap<>();
        readTenantsFromZooKeeper(curator).forEach(t -> futures.put(t, bootstrapExecutor.submit(() -> bootstrapTenant(t))));

        // Wait for all tenants to be created
        Set<TenantName> failed = new HashSet<>();
        for (Map.Entry<TenantName, Future<?>> f : futures.entrySet()) {
            TenantName tenantName = f.getKey();
            try {
                f.getValue().get();
            } catch (ExecutionException e) {
                log.log(Level.WARNING, "Failed to create tenant " + tenantName, e);
                failed.add(tenantName);
            } catch (InterruptedException e) {
                log.log(Level.WARNING, "Interrupted while creating tenant '" + tenantName + "'", e);
            }
        }

        if (failed.size() > 0)
            throw new RuntimeException("Could not create all tenants when bootstrapping, failed to create: " + failed);

        metricUpdater.setTenants(tenants.size());
        bootstrapExecutor.shutdown();
        try {
            bootstrapExecutor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
        } catch (InterruptedException e) {
            throw new RuntimeException("Executor for creating tenants did not terminate within timeout");
        }
    }

    // Use when bootstrapping an existing tenant based on ZooKeeper data
    protected void bootstrapTenant(TenantName tenantName) {
        try (Lock lock = tenantLocks.lock(tenantName)) {
            createTenant(tenantName, readCreatedTimeFromZooKeeper(tenantName));
        }
    }

    public Instant readCreatedTimeFromZooKeeper(TenantName tenantName) {
        Optional<Stat> stat = curator.getStat(getTenantPath(tenantName));
        if (stat.isPresent())
            return Instant.ofEpochMilli(stat.get().getCtime());
        else
            return clock.instant();
    }

    // Creates tenant and all its dependencies. This also includes loading active applications
    private Tenant createTenant(TenantName tenantName, Instant created) {
        if (tenants.containsKey(tenantName)) return getTenant(tenantName);

        Instant start = clock.instant();
        log.log(Level.FINE, () -> "Adding tenant '" + tenantName);
        TenantApplications applicationRepo =
                new TenantApplications(tenantName,
                                       curator,
                                       zkApplicationWatcherExecutor,
                                       zkCacheExecutor,
                                       metrics,
                                       configActivationListener,
                                       configserverConfig,
                                       hostRegistry,
                                       new TenantFileSystemDirs(configServerDB, tenantName),
                                       clock,
                                       flagSource);
        SessionPreparer sessionPreparer = new SessionPreparer(modelFactoryRegistry,
                                                              fileDistributionFactory,
                                                              deployHelperExecutor,
                                                              hostProvisionerProvider,
                                                              configserverConfig,
                                                              configDefinitionRepo,
                                                              curator,
                                                              zone,
                                                              flagSource,
                                                              secretStore);
        SessionRepository sessionRepository = new SessionRepository(tenantName,
                                                                    applicationRepo,
                                                                    sessionPreparer,
                                                                    curator,
                                                                    metrics,
                                                                    zkSessionWatcherExecutor,
                                                                    fileDistributionFactory,
                                                                    flagSource,
                                                                    zkCacheExecutor,
                                                                    secretStore,
                                                                    hostProvisionerProvider,
                                                                    configserverConfig,
                                                                    configServerDB,
                                                                    zone,
                                                                    clock,
                                                                    modelFactoryRegistry,
                                                                    configDefinitionRepo,
                                                                    zookeeperServerConfig.juteMaxBuffer());
        log.log(Level.FINE, "Adding tenant '" + tenantName + "'" + ", created " + created +
                            ". Bootstrapping in " + Duration.between(start, clock.instant()));
        Tenant tenant = new Tenant(tenantName, sessionRepository, applicationRepo, created);
        createAndWriteTenantMetaData(tenant);
        tenants.putIfAbsent(tenantName, tenant);
        notifyNewTenant(tenant);
        return tenant;
    }

    /**
     * Returns a default (compatibility with single tenant config requests) tenant
     *
     * @return default tenant
     */
    public Tenant defaultTenant() {
        try (Lock lock = tenantLocks.lock(DEFAULT_TENANT)) {
            return tenants.get(DEFAULT_TENANT);
        }
    }

    private void removeUnusedApplications() {
        getAllTenants().forEach(tenant -> tenant.getApplicationRepo().removeUnusedApplications());
    }

    private void notifyNewTenant(Tenant tenant) {
        tenantListener.onTenantCreate(tenant);
    }

    private void notifyRemovedTenant(TenantName name) {
        hostRegistry.removeHosts(name);
        tenantListener.onTenantDelete(name);
    }

    /**
     * Creates the tenants that should always be present into ZooKeeper. Will not fail if the node
     * already exists, as this is OK and might happen when several config servers start at the
     * same time and try to call this method.
     */
    private void createSystemTenants(ConfigserverConfig configserverConfig) {
        List<TenantName> systemTenants = new ArrayList<>();
        systemTenants.add(DEFAULT_TENANT);
        if (configserverConfig.hostedVespa()) systemTenants.add(HOSTED_VESPA_TENANT);

        for (final TenantName tenantName : systemTenants) {
            try {
                writeTenantPath(tenantName);
            } catch (RuntimeException e) {
                // Do nothing if we get NodeExistsException
                if (e.getCause().getClass() != KeeperException.NodeExistsException.class) {
                    throw e;
                }
            }
        }
    }

    /**
     * Writes the path of the given tenant into ZooKeeper, for watchers to react on
     *
     * @param name name of the tenant
     */
    private void writeTenantPath(TenantName name) {
        try (Lock lock = tenantLocks.lock(name)) {
            curator.createAtomically(TenantRepository.getTenantPath(name),
                                     TenantRepository.getSessionsPath(name),
                                     TenantRepository.getApplicationsPath(name),
                                     TenantRepository.getLocksPath(name));
        }
    }

    /**
     * Removes the given tenant from ZooKeeper and filesystem. Assumes that tenant exists.
     *
     * @param name name of the tenant
     */
    public void deleteTenant(TenantName name) {
        if (name.equals(DEFAULT_TENANT))
            throw new IllegalArgumentException("Deleting 'default' tenant is not allowed");
        if ( ! tenants.containsKey(name))
            throw new IllegalArgumentException("Deleting '" + name + "' failed, tenant does not exist");

        log.log(Level.INFO, "Deleting tenant '" + name + "'");
        // Deletes the tenant tree from ZooKeeper (application and session status for the tenant)
        // and triggers Tenant.close().
        try (Lock lock = tenantLocks.lock(name)) {
            Path path = tenants.get(name).getPath();
            closeTenant(name);
            curator.delete(path);
        }
    }

    private void closeTenant(TenantName name) {
        try (Lock lock = tenantLocks.lock(name)) {
            Tenant tenant = tenants.remove(name);
            if (tenant == null)
                throw new IllegalArgumentException("Closing '" + name + "' failed, tenant does not exist");

            log.log(Level.INFO, "Closing tenant '" + name + "'");
            notifyRemovedTenant(name);
            tenant.close();
        }
    }

    /**
     * A helper to format a log preamble for messages with a tenant and app id
     *
     * @param app the app
     * @return the log string
     */
    public static String logPre(ApplicationId app) {
        if (DEFAULT_TENANT.equals(app.tenant())) return "";
        return "app:" + app.toFullString() + " ";
    }

    /**
     * A helper to format a log preamble for messages with a tenant
     *
     * @param tenant tenant
     * @return the log string
     */
    public static String logPre(TenantName tenant) {
        if (DEFAULT_TENANT.equals(tenant)) return "";
        return "tenant:" + tenant.value() + " ";
    }

    private void stateChanged(CuratorFramework framework, ConnectionState connectionState) {
        switch (connectionState) {
            case CONNECTED:
                metricUpdater.incZKConnected();
                break;
            case SUSPENDED:
                metricUpdater.incZKSuspended();
                break;
            case RECONNECTED:
                metricUpdater.incZKReconnected();
                break;
            case LOST:
                metricUpdater.incZKConnectionLost();
                break;
            case READ_ONLY:
                // NOTE: Should not be relevant for configserver.
                break;
        }
    }

    private void childEvent(CuratorFramework framework, PathChildrenCacheEvent event) {
        switch (event.getType()) {
            case CHILD_ADDED:
                TenantName t1 = getTenantNameFromEvent(event);
                if ( ! tenants.containsKey(t1))
                    zkApplicationWatcherExecutor.execute(t1, () -> bootstrapTenant(t1));
                break;
            case CHILD_REMOVED:
                TenantName t2 = getTenantNameFromEvent(event);
                if (tenants.containsKey(t2))
                    zkApplicationWatcherExecutor.execute(t2, () -> deleteTenant(t2));
                break;
            default:
                break; // Nothing to do
        }
        metricUpdater.setTenants(tenants.size());
    }

    private TenantName getTenantNameFromEvent(PathChildrenCacheEvent event) {
        String path = event.getData().getPath();
        String[] pathElements = path.split("/");
        if (pathElements.length == 0)
            throw new IllegalArgumentException("Path " + path + " does not contain a tenant name");
        return TenantName.from(pathElements[pathElements.length - 1]);
    }

    public void close() {
        directoryCache.close();
        fileDistributionFactory.close();
        try {
            zkCacheExecutor.shutdown();
            checkForRemovedApplicationsService.shutdown();
            zkApplicationWatcherExecutor.shutdownAndWait();
            zkSessionWatcherExecutor.shutdownAndWait();
            zkCacheExecutor.awaitTermination(50, TimeUnit.SECONDS);
            checkForRemovedApplicationsService.awaitTermination(50, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            log.log(Level.WARNING, "Interrupted while shutting down.", e);
            Thread.currentThread().interrupt();
        }
    }

    public boolean checkThatTenantExists(TenantName tenant) {
        return tenants.containsKey(tenant);
    }

    /** Returns the tenant with the given name, or {@code null} if this does not exist. */
    public Tenant getTenant(TenantName tenantName) {
        return tenants.get(tenantName);
    }

    public Set<TenantName> getAllTenantNames() {
        return ImmutableSet.copyOf(tenants.keySet());
    }

    public Collection<Tenant> getAllTenants() {
        return ImmutableSet.copyOf(tenants.values());
    }

    /**
     * Gets zookeeper path for tenant data
     *
     * @param tenantName tenant name
     * @return a {@link com.yahoo.path.Path} to the zookeeper data for a tenant
     */
    public static Path getTenantPath(TenantName tenantName) {
        return tenantsPath.append(tenantName.value());
    }

    /**
     * Gets zookeeper path for session data for a tenant
     *
     * @param tenantName tenant name
     * @return a {@link com.yahoo.path.Path} to the zookeeper sessions data for a tenant
     */
    public static Path getSessionsPath(TenantName tenantName) {
        return getTenantPath(tenantName).append(Tenant.SESSIONS);
    }

    /**
     * Gets zookeeper path for application data for a tenant
     *
     * @param tenantName tenant name
     * @return a {@link com.yahoo.path.Path} to the zookeeper application data for a tenant
     */
    public static Path getApplicationsPath(TenantName tenantName) {
        return getTenantPath(tenantName).append(Tenant.APPLICATIONS);
    }

    /**
     * Gets zookeeper path for locks for a tenant's applications. This is never cleaned, but shouldn't be a problem.
     */
    public static Path getLocksPath(TenantName tenantName) {
        return locksPath.append(tenantName.value());
    }

    public static Path getBarriersPath() {
        return barriersPath;
    }

    public com.yahoo.vespa.curator.Curator getCurator() { return curator; }

    public HostProvisionerProvider hostProvisionerProvider() { return hostProvisionerProvider; }

}
