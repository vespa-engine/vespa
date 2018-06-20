// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Version;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactoryFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Handles request for supermodel config.
 *
 * @author Ulf Lilleengen
 */
public class SuperModelRequestHandler implements RequestHandler {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SuperModelRequestHandler.class.getName());
    private volatile SuperModelController handler;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final ConfigResponseFactory responseFactory;
    private final SuperModelManager superModelManager;
    private volatile boolean enabled = false;

    /**
     * Creates a supermodel controller
     */
    @Inject
    public SuperModelRequestHandler(ConfigDefinitionRepo configDefinitionRepo,
                                    ConfigserverConfig configserverConfig,
                                    SuperModelManager superModelManager) {
        this.configDefinitionRepo = configDefinitionRepo;
        this.responseFactory = ConfigResponseFactoryFactory.createFactory(configserverConfig);
        this.superModelManager = superModelManager;
        updateHandler();
    }

    /**
     * Signals that config has been reloaded for an {@link com.yahoo.vespa.config.server.application.Application}
     * belonging to a tenant.
     *
     * @param applicationSet The reloaded set of {@link com.yahoo.vespa.config.server.application.Application}.
     */
    public synchronized void reloadConfig(ApplicationSet applicationSet) {
        superModelManager.configActivated(applicationSet);
        updateHandler();
    }

    public synchronized void removeApplication(ApplicationId applicationId) {
        superModelManager.applicationRemoved(applicationId);
        updateHandler();
    }

    private void updateHandler() {
        handler = new SuperModelController(
                superModelManager.getSuperModelConfigProvider(),
                configDefinitionRepo,
                superModelManager.getGeneration(),
                responseFactory);
    }

    public SuperModelController getHandler() { return handler; }

    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        log.log(LogLevel.DEBUG, () -> "SuperModelRequestHandler resolving " + req + " for app id '" + appId + "'");
        if (handler != null) {
            ConfigResponse configResponse = handler.resolveConfig(req);
            log.log(LogLevel.DEBUG, () -> "SuperModelRequestHandler returning response for config " + req +
                    " with generation " + configResponse.getGeneration());
            return configResponse;
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
