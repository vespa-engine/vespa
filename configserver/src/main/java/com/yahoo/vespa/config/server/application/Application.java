// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.component.Version;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.UnknownConfigDefinitionException;
import com.yahoo.vespa.config.server.modelfactory.ModelResult;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;

import java.util.Objects;
import java.util.Set;

/**
 * A Vespa application for a specific version of Vespa. It holds data and metadata associated with
 * a Vespa application, i.e. generation, model and zookeeper data, as well as methods for resolving config
 * and other queries against the model.
 *
 * @author hmusum
 */
public class Application implements ModelResult {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Application.class.getName());
    private final long appGeneration; // The config generation for this application
    private final boolean internalRedeploy;
    private final Version vespaVersion;
    private final Model model;
    private final ServerCache cache;
    private final MetricUpdater metricUpdater;
    private final ApplicationId app;
    private final BooleanFlag useConfigServerCache;

    public Application(Model model, ServerCache cache, long appGeneration, boolean internalRedeploy,
                       Version vespaVersion, MetricUpdater metricUpdater, ApplicationId app) {
        this(model, cache, appGeneration, internalRedeploy, vespaVersion, metricUpdater, app, new InMemoryFlagSource());
    }

    public Application(Model model, ServerCache cache, long appGeneration, boolean internalRedeploy,
                       Version vespaVersion, MetricUpdater metricUpdater, ApplicationId app, FlagSource flagSource) {
        Objects.requireNonNull(model, "The model cannot be null");
        this.model = model;
        this.cache = cache;
        this.appGeneration = appGeneration;
        this.internalRedeploy = internalRedeploy;
        this.vespaVersion = vespaVersion;
        this.metricUpdater = metricUpdater;
        this.app = app;
        this.useConfigServerCache = Flags.USE_CONFIG_SERVER_CACHE
                .with(FetchVector.Dimension.APPLICATION_ID, app.serializedForm())
                .bindTo(flagSource);
    }

    /**
     * Returns the generation for the config we are currently serving
     *
     * @return the config generation
     */
    public Long getApplicationGeneration() { return appGeneration; }

    /** Returns the application model, never null */
    @Override
    public Model getModel() { return model; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("application '").append(app.application().value()).append("', ");
        sb.append("generation ").append(appGeneration).append(", ");
        sb.append("vespa version ").append(vespaVersion);
        return sb.toString();
    }

    public ApplicationInfo toApplicationInfo() {
        return new ApplicationInfo(app, appGeneration, model);
    }

    public ServerCache getCache() {
        return cache;
    }

    public ApplicationId getId() {
        return app;
    }

    public Version getVespaVersion() {
        return vespaVersion;
    }

    /**
     * Gets a config from ZK. Returns null if not found.
     */
    public ConfigResponse resolveConfig(GetConfigRequest req, ConfigResponseFactory responseFactory) {
        long start = System.currentTimeMillis();
        metricUpdater.incrementRequests();
        ConfigKey<?> configKey = req.getConfigKey();
        String defMd5 = configKey.getMd5();
        if (defMd5 == null || defMd5.isEmpty()) {
            defMd5 = ConfigUtils.getDefMd5(req.getDefContent().asList());
        }
        ConfigCacheKey cacheKey = new ConfigCacheKey(configKey, defMd5);
        if (logDebug()) {
            debug("Resolving config " + cacheKey);
        }

        if (useCache(req)) {
            ConfigResponse config = cache.get(cacheKey);
            if (config != null) {
                if (logDebug()) {
                    debug("Found config " + cacheKey + " in cache");
                }
                metricUpdater.incrementProcTime(System.currentTimeMillis() - start);
                return config;
            }
        }

        ConfigDefinition def = getTargetDef(req);
        if (def == null) {
            metricUpdater.incrementFailedRequests();
            throw new UnknownConfigDefinitionException("Unable to find config definition for '" + configKey.getNamespace() + "." + configKey.getName());
        }
        if (logDebug()) {
            debug("Resolving " + configKey + " with config definition " + def);
        }
        ConfigPayload payload = model.getConfig(configKey, def);
        if (payload == null) {
            metricUpdater.incrementFailedRequests();
            throw new ConfigurationRuntimeException("Unable to resolve config " + configKey);
        }

        ConfigResponse configResponse = responseFactory.createResponse(payload, def.getCNode(), appGeneration, internalRedeploy);
        metricUpdater.incrementProcTime(System.currentTimeMillis() - start);
        if (useCache(req)) {
            cache.put(cacheKey, configResponse, configResponse.getConfigMd5());
            metricUpdater.setCacheConfigElems(cache.configElems());
            metricUpdater.setCacheChecksumElems(cache.checkSumElems());
        }
        return configResponse;
    }

    private boolean useCache(GetConfigRequest request) {
        if (request.noCache())
            return false;
        else
            return useConfigServerCache.value();
    }

    private boolean logDebug() {
        return log.isLoggable(LogLevel.DEBUG);
    }

    private void debug(String message) {
        log.log(LogLevel.DEBUG, TenantRepository.logPre(getId())+message);
    }

    private ConfigDefinition getTargetDef(GetConfigRequest req) {
        ConfigKey<?> configKey = req.getConfigKey();
        DefContent def = req.getDefContent();
        ConfigDefinitionKey configDefinitionKey = new ConfigDefinitionKey(configKey.getName(), configKey.getNamespace());
        if (def.isEmpty()) {
            if (logDebug()) {
                debug("No config schema in request for " + configKey);
            }
            return cache.getDef(configDefinitionKey);
        } else {
            if (logDebug()) {
                debug("Got config schema from request, length:" + def.asList().size() + " : " + configKey);
            }
            return new ConfigDefinition(configKey.getName(), def.asStringArray());
        }
    }

    void updateHostMetrics(int numHosts) {
        metricUpdater.setHosts(numHosts);
    }

    // For testing only
    ConfigResponse resolveConfig(GetConfigRequest req) {
        return resolveConfig(req, new UncompressedConfigResponseFactory());
    }

    public Set<ConfigKey<?>> allConfigsProduced() {
        return model.allConfigsProduced();
    }

    public Set<String> allConfigIds() {
        return model.allConfigIds();
    }
}
