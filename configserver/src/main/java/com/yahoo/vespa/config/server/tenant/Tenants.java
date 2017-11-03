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
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.zookeeper.KeeperException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * This component will monitor the set of tenants in the config server by watching in ZooKeeper.
 * It will set up Tenant objects accordingly, which will manage the config sessions per tenant.
 * This class will read the preexisting set of tenants from ZooKeeper at startup. (For now it will also
 * create a default tenant since that will be used for API that do no know about tenants or have not yet
 * implemented support for it).
 *
 * This instance is called from two different threads, the http handler threads and the zookeeper watcher threads.
 * To create or delete a tenant, the handler calls {@link Tenants#addTenant} and {@link Tenants#deleteTenant} methods.
 * This will delete shared state from zookeeper, and return, so it does not mean a tenant is immediately deleted.
 *
 * Once a tenant is deleted from zookeeper, the zookeeper watcher thread will get notified on all configservers, and
 * shutdown and delete any per-configserver state.
 *
 * @author Vegard Havdal
 * @author Ulf Lilleengen
 */
// TODO: Rename to TenantRepository
public class Tenants implements ConnectionStateListener, PathChildrenCacheListener {

    public static final TenantName HOSTED_VESPA_TENANT = TenantName.from("hosted-vespa");
    private static final TenantName DEFAULT_TENANT = TenantName.defaultName();

    private static final Path tenantsPath = Path.fromString("/config/v2/tenants/");
    private static final Path vespaPath = Path.fromString("/vespa");

    private static final Logger log = Logger.getLogger(Tenants.class.getName());

    private final Map<TenantName, Tenant> tenants = new LinkedHashMap<>();
    private final GlobalComponentRegistry globalComponentRegistry;
    private final List<TenantListener> tenantListeners = Collections.synchronizedList(new ArrayList<>());
    private final Curator curator;

    private final MetricUpdater metricUpdater;
    private final ExecutorService pathChildrenExecutor = Executors.newFixedThreadPool(1, ThreadFactoryFactory.getThreadFactory(Tenants.class.getName()));
    private final Curator.DirectoryCache directoryCache;


    /**
     * New instance from the tenants in the given component registry's ZooKeeper data.
     * 
     * @param globalComponentRegistry a {@link com.yahoo.vespa.config.server.GlobalComponentRegistry}
     * @throws Exception is creating the Tenants instance fails
     */
    @Inject
    public Tenants(GlobalComponentRegistry globalComponentRegistry, Metrics metrics) throws Exception {
        this.globalComponentRegistry = globalComponentRegistry;
        this.curator = globalComponentRegistry.getCurator();
        metricUpdater = metrics.getOrCreateMetricUpdater(Collections.emptyMap());
        this.tenantListeners.add(globalComponentRegistry.getTenantListener());
        curator.framework().getConnectionStateListenable().addListener(this);

        curator.create(tenantsPath);
        createSystemTenants(globalComponentRegistry.getConfigserverConfig());
        curator.create(vespaPath);

        this.directoryCache = curator.createDirectoryCache(tenantsPath.getAbsolute(), false, false, pathChildrenExecutor);
        directoryCache.start();
        directoryCache.addListener(this);
        createTenants();
        notifyTenantsLoaded();
    }

    /**
     * New instance containing the given tenants. This will not create Zookeeper watches. For testing only
     * @param globalComponentRegistry a {@link com.yahoo.vespa.config.server.GlobalComponentRegistry} instance
     * @param metrics a {@link com.yahoo.vespa.config.server.monitoring.Metrics} instance
     * @param tenants a collection of {@link Tenant}s
     */
    public Tenants(GlobalComponentRegistry globalComponentRegistry, Metrics metrics, Collection<Tenant> tenants) {
        this.globalComponentRegistry = globalComponentRegistry;
        this.curator = globalComponentRegistry.getCurator();
        metricUpdater = metrics.getOrCreateMetricUpdater(Collections.emptyMap());
        this.tenantListeners.add(globalComponentRegistry.getTenantListener());
        curator.create(tenantsPath);
        this.directoryCache = curator.createDirectoryCache(tenantsPath.getAbsolute(), false, false, pathChildrenExecutor);
        this.tenants.putAll(addTenants(tenants));
    }

    private void notifyTenantsLoaded() {
        for (TenantListener tenantListener : tenantListeners) {
            tenantListener.onTenantsLoaded();
        }
    }

    // Pre-condition: tenants path needs to exist in zk
    private LinkedHashMap<TenantName, Tenant> addTenants(Collection<Tenant> newTenants) {
        LinkedHashMap<TenantName, Tenant> sessionTenants = new LinkedHashMap<>();
        for (Tenant t : newTenants) {
            sessionTenants.put(t.getName(), t);
        }
        log.log(LogLevel.DEBUG, "Tenants at startup: " + sessionTenants);
        metricUpdater.setTenants(tenants.size());
        return sessionTenants;
    }
    
    public synchronized void addTenant(TenantName tenantName) throws Exception {
        writeTenantPath(tenantName);
        createTenant(tenantName);
    }

    /**
     * Reads the set of tenants in patch cache.
     *
     * @return a set of tenant names
     */
    private Set<TenantName> readTenantsFromZooKeeper() {
        Set<TenantName> tenants = new LinkedHashSet<>();
        for (String tenant : curator.getChildren(tenantsPath)) {
            tenants.add(TenantName.from(tenant));
        }
        return tenants;
    }

    synchronized void createTenants() throws Exception {
        Set<TenantName> allTenants = readTenantsFromZooKeeper();
        log.log(LogLevel.DEBUG, "Create tenants, tenants found in zookeeper: " + allTenants);
        checkForRemovedTenants(allTenants);
        checkForAddedTenants(allTenants);
        metricUpdater.setTenants(tenants.size());
    }

    private void checkForRemovedTenants(Set<TenantName> newTenants) {
        Map<TenantName, Tenant> current = new LinkedHashMap<>(tenants);
        for (Map.Entry<TenantName, Tenant> entry : current.entrySet()) {
            TenantName tenant = entry.getKey();
            if (!newTenants.contains(tenant)) {
                notifyRemovedTenant(tenant);
                entry.getValue().close();
                tenants.remove(tenant);
            }
        }
    }

    private void checkForAddedTenants(Set<TenantName> newTenants) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(globalComponentRegistry.getConfigserverConfig().numParallelTenantLoaders());
        for (TenantName tenantName : newTenants) {
            // Note: the http handler will check if the tenant exists, and throw accordingly
            if (!tenants.containsKey(tenantName)) {
                executor.execute(() -> {
                    createTenant(tenantName);
                });
            }
        }
        executor.shutdown();
        executor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
    }

    private void createTenant(TenantName tenantName) {
        try {
            Tenant tenant = TenantBuilder.create(globalComponentRegistry, tenantName, getTenantPath(tenantName)).build();
            notifyNewTenant(tenant);
            tenants.put(tenantName, tenant);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Error loading tenant '" + tenantName + "', skipping.", e);
        }
    }

    /**
     * Returns a default (compatibility with single tenant config requests) tenant
     *
     * @return default tenant
     */
    public synchronized Tenant defaultTenant() {
        return tenants.get(DEFAULT_TENANT);
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
        Path tenantPath = getTenantPath(name);
        curator.createAtomically(tenantPath, tenantPath.append(Tenant.SESSIONS), tenantPath.append(Tenant.APPLICATIONS));
    }

    /**
     * Removes the given tenant from ZooKeeper and filesystem. Assumes that tenant exists.
     *
     * @param name name of the tenant
     * @return this Tenants instance
     */
    public synchronized Tenants deleteTenant(TenantName name) {
        Tenant tenant = tenants.get(name);
        tenant.delete();
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
        if (TenantName.defaultName().equals(app.tenant())) return "";
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

    @Override
    public void stateChanged(CuratorFramework framework, ConnectionState connectionState) {
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

    @Override
    public void childEvent(CuratorFramework framework, PathChildrenCacheEvent event) throws Exception {
        switch (event.getType()) {
            case CHILD_ADDED:
            case CHILD_REMOVED:
                createTenants();
                break;
        }
    }

    public void close() {
        directoryCache.close();
        pathChildrenExecutor.shutdown();
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
     * @param tenantName tenant name
     * @return a {@link com.yahoo.path.Path} to the zookeeper data for a tenant
     */
    public static Path getTenantPath(TenantName tenantName) {
        return tenantsPath.append(tenantName.value());
    }

}
