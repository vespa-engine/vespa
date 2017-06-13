// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.model.SuperModel;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactoryFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Handles request for supermodel config
 *
 * @author lulf
 * @since 5.9
 */
public class SuperModelRequestHandler implements RequestHandler {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SuperModelRequestHandler.class.getName());
    private volatile SuperModelController handler;
    private final GenerationCounter generationCounter;
    private final Zone zone;
    private final long masterGeneration;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final ConfigResponseFactory responseFactory;
    private volatile boolean enabled = false;

    /**
     * Creates a supermodel controller
     */
    @Inject
    public SuperModelRequestHandler(GenerationCounter generationCounter, ConfigDefinitionRepo configDefinitionRepo,
                                    ConfigserverConfig configserverConfig, NodeFlavors nodeFlavors) {
        this.generationCounter = generationCounter;
        this.configDefinitionRepo = configDefinitionRepo;
        this.masterGeneration = configserverConfig.masterGeneration();
        this.responseFactory = ConfigResponseFactoryFactory.createFactory(configserverConfig);
        this.zone = new Zone(configserverConfig, nodeFlavors);
        this.handler = createNewHandler(Collections.emptyMap());
    }

    /**
     * Signals that config has been reloaded for an {@link com.yahoo.vespa.config.server.application.Application}
     * belonging to a tenant.
     *
     * TODO: This is a bit too complex I think.
     *
     * @param tenant Name of tenant owning the application.
     * @param applicationSet The reloaded set of {@link com.yahoo.vespa.config.server.application.Application}.
     */
    public synchronized void reloadConfig(TenantName tenant, ApplicationSet applicationSet) {
        Map<TenantName, Map<ApplicationId, Application>> newModels = createModelCopy();
        if (!newModels.containsKey(tenant)) {
            newModels.put(tenant, new LinkedHashMap<>());
        }
        // TODO: Should supermodel care about multiple versions?
        newModels.get(tenant).put(applicationSet.getId(), applicationSet.getForVersionOrLatest(Optional.empty()));
        handler = createNewHandler(newModels);
    }

    public synchronized void removeApplication(ApplicationId applicationId) {
        Map<TenantName, Map<ApplicationId, Application>> newModels = createModelCopy();
        if (newModels.containsKey(applicationId.tenant())) {
            newModels.get(applicationId.tenant()).remove(applicationId);
            if (newModels.get(applicationId.tenant()).isEmpty()) {
                newModels.remove(applicationId.tenant());
            }
        }
        handler = createNewHandler(newModels);
    }

    private SuperModelController createNewHandler(Map<TenantName, Map<ApplicationId, Application>> newModels) {
        long generation = generationCounter.get() + masterGeneration;
        SuperModel model = new SuperModel(newModels, zone);
        return new SuperModelController(model, configDefinitionRepo, generation, responseFactory);
    }

    private Map<TenantName, Map<ApplicationId, Application>> getCurrentModels() {
        if (handler != null) {
            return handler.getSuperModel().applicationModels();
        } else {
            return new LinkedHashMap<>();
        }
    }

    private Map<TenantName, Map<ApplicationId, Application>> createModelCopy() {
        Map<TenantName, Map<ApplicationId, Application>> currentModels = getCurrentModels();
        Map<TenantName, Map<ApplicationId, Application>> newModels = new LinkedHashMap<>();
        for (Map.Entry<TenantName, Map<ApplicationId, Application>> entry : currentModels.entrySet()) {
            Map<ApplicationId, Application> appMap = new LinkedHashMap<>();
            newModels.put(entry.getKey(), appMap);
            for (Map.Entry<ApplicationId, Application> appEntry : entry.getValue().entrySet()) {
                appMap.put(appEntry.getKey(), appEntry.getValue());
            }
        }
        return newModels;
    }

    public SuperModelController getHandler() { return handler; }

    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        log.log(LogLevel.DEBUG, "SuperModelRequestHandler resolving " + req + " for app id '" + appId + "'");
        if (handler != null) {
            return handler.resolveConfig(req);
        }
        return null;
    }

    public <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> configClass, ApplicationId applicationId, String configId) throws IOException {
        return handler.getConfig(configClass, applicationId, configId);
    }

    @Override
    public Set<ConfigKey<?>> listConfigs(ApplicationId appId, Optional<Version> vespaVersion, boolean recursive) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<ConfigKey<?>> listNamedConfigs(ApplicationId appId, Optional<Version> vespaVersion, ConfigKey<?> key, boolean recursive) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced(ApplicationId appId, Optional<Version> vespaVersion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> allConfigIds(ApplicationId appID, Optional<Version> vespaVersion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasApplication(ApplicationId appId, Optional<Version> vespaVersion) {
        return enabled && appId.equals(ApplicationId.global());
    }

    @Override
    public ApplicationId resolveApplicationId(String hostName) {
        return ApplicationId.global();
    }

    public void enable() {
        enabled = true;
    }
}
