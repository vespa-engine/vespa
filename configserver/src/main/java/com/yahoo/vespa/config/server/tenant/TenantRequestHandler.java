// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import java.time.Clock;
import java.util.*;

import com.yahoo.config.provision.Version;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.application.ApplicationMapper;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.ReloadListener;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.application.VersionDoesNotExistException;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;

/**
 * A per tenant request handler, for handling reload (activate application) and getConfig requests for
 * a set of applications belonging to a tenant.
 *
 * @author Harald Musum
 */
public class TenantRequestHandler implements RequestHandler, ReloadHandler, HostValidator<ApplicationId> {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(TenantRequestHandler.class.getName());

    private final Metrics metrics;
    private final TenantName tenant;
    private final List<ReloadListener> reloadListeners;
    private final ConfigResponseFactory responseFactory;

    private final HostRegistry<ApplicationId> hostRegistry;
    private final ApplicationMapper applicationMapper = new ApplicationMapper();
    private final MetricUpdater tenantMetricUpdater;
    private final Clock clock = Clock.systemUTC();

    public TenantRequestHandler(Metrics metrics,
                                TenantName tenant,
                                List<ReloadListener> reloadListeners,
                                ConfigResponseFactory responseFactory,
                                HostRegistries hostRegistries) {
        this.metrics = metrics;
        this.tenant = tenant;
        this.reloadListeners = reloadListeners;
        this.responseFactory = responseFactory;
        tenantMetricUpdater = metrics.getOrCreateMetricUpdater(Metrics.createDimensions(tenant));
        hostRegistry = hostRegistries.createApplicationHostRegistry(tenant);
    }

    /**
     * Gets a config for the given app, or null if not found
     */
    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        Application application = getApplication(appId, vespaVersion);
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, TenantRepository.logPre(appId) + "Resolving for tenant '" + tenant + "' with handler for application '" + application + "'");
        }
        return application.resolveConfig(req, responseFactory);
    }

    // For testing only
    long getApplicationGeneration(ApplicationId appId, Optional<Version> vespaVersion) {
        Application application = getApplication(appId, vespaVersion);
        return application.getApplicationGeneration();
    }

    private void notifyReloadListeners(ApplicationSet applicationSet) {
        for (ReloadListener reloadListener : reloadListeners) {
            reloadListener.hostsUpdated(tenant, hostRegistry.getAllHosts());
            reloadListener.configActivated(applicationSet);
        }
    }

    /**
     * Activates the config of the given app. Notifies listeners
     * @param applicationSet the {@link ApplicationSet} to be reloaded
     */
    public void reloadConfig(ApplicationSet applicationSet) {
        setLiveApp(applicationSet);
        notifyReloadListeners(applicationSet);
    }

    @Override
    public void removeApplication(ApplicationId applicationId) {
        if (applicationMapper.hasApplication(applicationId, clock.instant())) {
            applicationMapper.remove(applicationId);
            hostRegistry.removeHostsForKey(applicationId);
            reloadListenersOnRemove(applicationId);
            tenantMetricUpdater.setApplications(applicationMapper.numApplications());
            metrics.removeMetricUpdater(Metrics.createDimensions(applicationId));
        }
    }

    @Override
    public void removeApplicationsExcept(Set<ApplicationId> applications) {
        for (ApplicationId activeApplication : applicationMapper.listApplicationIds()) {
            if (! applications.contains(activeApplication)) {
                log.log(LogLevel.INFO, "Will remove deleted application " + activeApplication.toShortString());
                removeApplication(activeApplication);
            }
        }
    }

    private void reloadListenersOnRemove(ApplicationId applicationId) {
        for (ReloadListener listener : reloadListeners) {
            listener.applicationRemoved(applicationId);
            listener.hostsUpdated(tenant, hostRegistry.getAllHosts());
        }
    }

    private void setLiveApp(ApplicationSet applicationSet) {
        ApplicationId id = applicationSet.getId();
        final Collection<String> hostsForApp = applicationSet.getAllHosts();
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
        ApplicationId applicationId = hostRegistry.getKeyForHost(hostName);
        if (applicationId == null) {
            applicationId = ApplicationId.defaultId();
        }
        return applicationId;
    }
    
    @Override
    public void verifyHosts(ApplicationId key, Collection<String> newHosts) {
        hostRegistry.verifyHosts(key, newHosts);
        for (ReloadListener reloadListener : reloadListeners) {
            reloadListener.verifyHostsAreAvailable(tenant, newHosts);
        }
    }

}
