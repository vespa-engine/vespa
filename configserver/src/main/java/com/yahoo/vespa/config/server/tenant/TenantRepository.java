// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.KeeperException;

import java.time.Duration;
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
 * create a default tenant since that will be used for API that do no know about tenants or have not yet
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
    private static final Path vespaPath = Path.fromString("/vespa");
    private static final Duration checkForRemovedApplicationsInterval = Duration.ofMinutes(1);
    private static final Logger log = Logger.getLogger(TenantRepository.class.getName());

    private final Map<TenantName, Tenant> tenants = Collections.synchronizedMap(new LinkedHashMap<>());
    private final GlobalComponentRegistry globalComponentRegistry;
    private final List<TenantListener> tenantListeners = Collections.synchronizedList(new ArrayList<>());
    private final Curator curator;

    private final MetricUpdater metricUpdater;
    private final ExecutorService pathChildrenExecutor = Executors.newFixedThreadPool(1, ThreadFactoryFactory.getThreadFactory(TenantRepository.class.getName()));
    private final ExecutorService bootstrapExecutor;
    private final ScheduledExecutorService checkForRemovedApplicationsService = new ScheduledThreadPoolExecutor(1);
    private final Optional<Curator.DirectoryCache> directoryCache;
    private final boolean throwExceptionIfBootstrappingFails;

    /**
     * Creates a new tenant repository
     * 
     * @param globalComponentRegistry a {@link com.yahoo.vespa.config.server.GlobalComponentRegistry}
     */
    @Inject
    public TenantRepository(GlobalComponentRegistry globalComponentRegistry) {
        this(globalComponentRegistry, true);
    }

    /**
     * Creates a new tenant repository
     *
     * @param globalComponentRegistry a {@link com.yahoo.vespa.config.server.GlobalComponentRegistry}
     * @param useZooKeeperWatchForTenantChanges set to false for tests where you want to control adding and deleting
     *                                          tenants yourself
     */
    public TenantRepository(GlobalComponentRegistry globalComponentRegistry, boolean useZooKeeperWatchForTenantChanges) {
        this.globalComponentRegistry = globalComponentRegistry;
        ConfigserverConfig configserverConfig = globalComponentRegistry.getConfigserverConfig();
        this.bootstrapExecutor = Executors.newFixedThreadPool(configserverConfig.numParallelTenantLoaders());
        this.throwExceptionIfBootstrappingFails = configserverConfig.throwIfBootstrappingTenantRepoFails();
        this.curator = globalComponentRegistry.getCurator();
        metricUpdater = globalComponentRegistry.getMetrics().getOrCreateMetricUpdater(Collections.emptyMap());
        this.tenantListeners.add(globalComponentRegistry.getTenantListener());
        curator.framework().getConnectionStateListenable().addListener(this::stateChanged);

        curator.create(tenantsPath);
        curator.create(locksPath);
        createSystemTenants(configserverConfig);
        curator.create(vespaPath);

        if (useZooKeeperWatchForTenantChanges) {
            this.directoryCache = Optional.of(curator.createDirectoryCache(tenantsPath.getAbsolute(), false, false, pathChildrenExecutor));
            this.directoryCache.get().start();
            this.directoryCache.get().addListener(this::childEvent);
        } else {
            this.directoryCache = Optional.empty();
        }
        log.log(LogLevel.DEBUG, "Creating all tenants");
        bootstrapTenants();
        notifyTenantsLoaded();
        log.log(LogLevel.DEBUG, "All tenants created");
        checkForRemovedApplicationsService.scheduleWithFixedDelay(this::removeUnusedApplications,
                                                                  checkForRemovedApplicationsInterval.getSeconds(),
                                                                  checkForRemovedApplicationsInterval.getSeconds(),
                                                                  TimeUnit.SECONDS);
    }

    private void notifyTenantsLoaded() {
        for (TenantListener tenantListener : tenantListeners) {
            tenantListener.onTenantsLoaded();
        }
    }

    public synchronized void addTenant(TenantName tenantName) {
        addTenant(TenantBuilder.create(globalComponentRegistry, tenantName));
    }

    public synchronized void addTenant(TenantBuilder builder) {
        writeTenantPath(builder.getTenantName());
        createTenant(builder);
    }

     private static Set<TenantName> readTenantsFromZooKeeper(Curator curator) {
        return curator.getChildren(tenantsPath).stream().map(TenantName::from).collect(Collectors.toSet());
    }

    private synchronized void updateTenants() {
        Set<TenantName> allTenants = readTenantsFromZooKeeper(curator);
        log.log(LogLevel.DEBUG, "Create tenants, tenants found in zookeeper: " + allTenants);
        checkForRemovedTenants(allTenants);
        allTenants.stream().filter(tenantName -> ! tenants.containsKey(tenantName)).forEach(this::createTenant);
        metricUpdater.setTenants(tenants.size());
    }

    private void checkForRemovedTenants(Set<TenantName> newTenants) {
        for (TenantName tenantName : ImmutableSet.copyOf(tenants.keySet())) {
            if (!newTenants.contains(tenantName)) {
                deleteTenant(tenantName);
            }
        }
    }

    private void bootstrapTenants() {
        // Keep track of tenants created
        Map<TenantName, Future<?>> futures = new HashMap<>();
        readTenantsFromZooKeeper(curator).forEach(t -> futures.put(t, bootstrapExecutor.submit(() -> createTenant(t))));

        // Wait for all tenants to be created
        Set<TenantName> failed = new HashSet<>();
        for (Map.Entry<TenantName, Future<?>> f : futures.entrySet()) {
            TenantName tenantName = f.getKey();
            try {
                f.getValue().get();
            } catch (ExecutionException e) {
                log.log(LogLevel.WARNING, "Failed to create tenant " + tenantName, e);
                failed.add(tenantName);
            } catch (InterruptedException e) {
                log.log(LogLevel.WARNING, "Interrupted while creating tenant '" + tenantName + "'", e);
            }
        }

        if (failed.size() > 0 && throwExceptionIfBootstrappingFails)
            throw new RuntimeException("Could not create all tenants when bootstrapping, failed to create: " + failed);

        metricUpdater.setTenants(tenants.size());
        bootstrapExecutor.shutdown();
        try {
            bootstrapExecutor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
        } catch (InterruptedException e) {
            throw new RuntimeException("Executor for creating tenants did not terminate within timeout");
        }
    }

    private void createTenant(TenantName tenantName) {
        createTenant(TenantBuilder.create(globalComponentRegistry, tenantName));
    }

    // Creates tenant and all its dependencies. This also includes loading active applications
    protected void createTenant(TenantBuilder builder) {
        TenantName tenantName = builder.getTenantName();
        if (tenants.containsKey(tenantName)) return;

        log.log(LogLevel.INFO, "Creating tenant '" + tenantName + "'");
        Tenant tenant = builder.build();
        notifyNewTenant(tenant);
        tenants.putIfAbsent(tenantName, tenant);
    }

    /**
     * Returns a default (compatibility with single tenant config requests) tenant
     *
     * @return default tenant
     */
    public synchronized Tenant defaultTenant() {
        return tenants.get(DEFAULT_TENANT);
    }


    private void removeUnusedApplications() {
        getAllTenants().forEach(tenant -> tenant.getApplicationRepo().removeUnusedApplications());
    }

    private void notifyNewTenant(Tenant tenant) {
        for (TenantListener listener : tenantListeners) {
            listener.onTenantCreate(tenant.getName(), tenant);
        }
    }

    private void notifyRemovedTenant(TenantName name) {
        for (TenantListener listener : tenantListeners) {
            listener.onTenantDelete(name);
        }
    }

    /**
     * Writes the tenants that should always be present into ZooKeeper. Will not fail if the node
     * already exists, as this is OK and might happen when several config servers start at the
     * same time and try to call this method.
     */
    private synchronized void createSystemTenants(ConfigserverConfig configserverConfig) {
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
    private synchronized void writeTenantPath(TenantName name) {
        curator.createAtomically(TenantRepository.getTenantPath(name),
                                 TenantRepository.getSessionsPath(name),
                                 TenantRepository.getApplicationsPath(name),
                                 TenantRepository.getLocksPath(name));
    }

    /**
     * Removes the given tenant from ZooKeeper and filesystem. Assumes that tenant exists.
     *
     * @param name name of the tenant
     * @return this TenantRepository instance
     */
    public synchronized TenantRepository deleteTenant(TenantName name) {
        if (name.equals(DEFAULT_TENANT))
            throw new IllegalArgumentException("Deleting 'default' tenant is not allowed");
        log.log(LogLevel.INFO, "Deleting tenant '" + name + "'");
        Tenant tenant = tenants.remove(name);
        if (tenant == null) {
            throw new IllegalArgumentException("Deleting '" + name + "' failed, tenant does not exist");
        }
        notifyRemovedTenant(name);
        tenant.close();
        return this;
    }

    // For unit testing
    String tenantZkPath(TenantName tenant) {
        return getTenantPath(tenant).getAbsolute();
    }
    
    /**
     * A helper to format a log preamble for messages with a tenant and app id
     * @param app the app
     * @return the log string
     */
    public static String logPre(ApplicationId app) {
        if (DEFAULT_TENANT.equals(app.tenant())) return "";
        StringBuilder ret = new StringBuilder()
            .append(logPre(app.tenant()))
            .append("app:"+app.application().value())
            .append(":"+app.instance().value())
            .append(" ");
        return ret.toString();
    }    

    /**
     * A helper to format a log preamble for messages with a tenant
     * @param tenant tenant
     * @return the log string
     */
    public static String logPre(TenantName tenant) {
        if (DEFAULT_TENANT.equals(tenant)) return "";
        StringBuilder ret = new StringBuilder()
            .append("tenant:" + tenant.value())
            .append(" ");
        return ret.toString();
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
            case CHILD_REMOVED:
                updateTenants();
                break;
        }
    }

    public void close() {
        directoryCache.ifPresent(Curator.DirectoryCache::close);
        try {
            pathChildrenExecutor.shutdown();
            checkForRemovedApplicationsService.shutdown();
            pathChildrenExecutor.awaitTermination(50, TimeUnit.SECONDS);
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

}
