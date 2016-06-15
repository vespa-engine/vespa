// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
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
import com.yahoo.vespa.config.server.model.SuperModel;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.yahoo.cloud.config.ElkConfig;

/**
 * Controls the lifetime of the {@link SuperModel} and the {@link SuperModelRequestHandler}.
 *
 * @author lulf
 * @since 5.9
 */
public class SuperModelController implements RequestHandler {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SuperModelController.class.getName());
    private volatile SuperModelRequestHandler handler;
    private final GenerationCounter generationCounter;
    private final Zone zone;
    private final long masterGeneration;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final ConfigResponseFactory responseFactory;
    private final ElkConfig elkConfig;
    private volatile boolean enabled = false;


    public SuperModelController(GenerationCounter generationCounter, ConfigDefinitionRepo configDefinitionRepo, ConfigserverConfig configserverConfig, ElkConfig elkConfig) {
        this.generationCounter = generationCounter;
        this.configDefinitionRepo = configDefinitionRepo;
        this.elkConfig = elkConfig;
        this.masterGeneration = configserverConfig.masterGeneration();
        this.responseFactory = ConfigResponseFactoryFactory.createFactory(configserverConfig);
        this.zone = new Zone(configserverConfig);
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

    private SuperModelRequestHandler createNewHandler(Map<TenantName, Map<ApplicationId, Application>> newModels) {
        long generation = generationCounter.get() + masterGeneration;
        SuperModel model = new SuperModel(newModels, elkConfig, zone);
        return new SuperModelRequestHandler(model, configDefinitionRepo, generation, responseFactory);
    }

    private Map<TenantName, Map<ApplicationId, Application>> getCurrentModels() {
        if (handler != null) {
            return handler.getSuperModel().getCurrentModels();
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

    public SuperModelRequestHandler getHandler() { return handler; }

    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        log.log(LogLevel.DEBUG, "SuperModelController resolving " + req + " for app id '" + appId + "'");
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
