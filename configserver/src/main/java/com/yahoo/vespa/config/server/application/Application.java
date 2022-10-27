// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.config.GenericConfig;
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
import com.yahoo.yolean.Exceptions;

import java.util.logging.Level;
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

    /** The config generation for this application. */
    private final long applicationGeneration;
    private final Version vespaVersion;
    private final Model model;
    private final ServerCache cache;
    private final MetricUpdater metricUpdater;
    private final ApplicationId app;

    public Application(Model model, ServerCache cache, long applicationGeneration,
                       Version vespaVersion, MetricUpdater metricUpdater, ApplicationId app) {
        Objects.requireNonNull(model, "The model cannot be null");
        this.model = model;
        this.cache = cache;
        this.applicationGeneration = applicationGeneration;
        this.vespaVersion = vespaVersion;
        this.metricUpdater = metricUpdater;
        this.app = app;
    }

    /** Returns the generation for the config we are currently serving. */
    public Long getApplicationGeneration() { return applicationGeneration; }

    /** Returns the application model, never null */
    @Override
    public Model getModel() { return model; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("application '").append(app.application().value()).append("', ");
        sb.append("generation ").append(applicationGeneration).append(", ");
        sb.append("vespa version ").append(vespaVersion);
        return sb.toString();
    }

    public ApplicationInfo toApplicationInfo() {
        return new ApplicationInfo(app, applicationGeneration, model);
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
        String defMd5 = req.getRequestDefMd5();
        if (defMd5 == null || defMd5.isEmpty()) {
            defMd5 = ConfigUtils.getDefMd5(req.getDefContent().asList());
        }
        ConfigCacheKey cacheKey = new ConfigCacheKey(configKey, defMd5);
        log.log(Level.FINE, () -> TenantRepository.logPre(getId()) + ("Resolving config " + cacheKey));

        ConfigResponse config;
        if (useCache(req)) {
            config = cache.computeIfAbsent(cacheKey, (ConfigCacheKey key) -> {
                var response = createConfigResponse(configKey, req, responseFactory);
                metricUpdater.setCacheConfigElems(cache.configElems());
                metricUpdater.setCacheChecksumElems(cache.checkSumElems());
                return response;
            });
        } else {
            config = createConfigResponse(configKey, req, responseFactory);
        }
        metricUpdater.incrementProcTime(System.currentTimeMillis() - start);
        return config;
    }

    private ConfigResponse createConfigResponse(ConfigKey<?> configKey, GetConfigRequest req, ConfigResponseFactory responseFactory) {
        ConfigDefinition def = getTargetDef(req);
        if (def == null) {
            metricUpdater.incrementFailedRequests();
            throw new UnknownConfigDefinitionException("Unable to find config definition for '" + configKey.getNamespace() + "." + configKey.getName());
        }
        log.log(Level.FINE, () -> TenantRepository.logPre(getId()) + "Resolving " + configKey + " with config definition " + def);

        var payload = createPayload(configKey, def);

        return responseFactory.createResponse(payload.getFirst(), applicationGeneration, payload.getSecond(), req.configPayloadChecksums());
    }

    private Pair<AbstractUtf8Array, Boolean> createPayload(ConfigKey<?> configKey, ConfigDefinition def) {
        try {
            ConfigInstance.Builder builder = model.getConfigInstance(configKey, def);
            boolean tempApplyOnRestart = builder.getApplyOnRestart();
            if (builder instanceof GenericConfig.GenericConfigBuilder) {
                return new Pair<>(((GenericConfig.GenericConfigBuilder) builder).getPayload().toUtf8Array(true),
                        tempApplyOnRestart);
            }
            else {
                String cacheBuilderClassNameForErrorReport = builder.getClass().getName();
                ConfigPayload payload;
                boolean applyOnRestart = false;
                try {
                    ConfigInstance instance = ConfigInstanceBuilder.buildInstance(builder, def.getCNode());
                    payload = ConfigPayload.fromInstance(instance);
                    applyOnRestart = tempApplyOnRestart;
                } catch (ConfigurationRuntimeException e) {
                    // This can happen in cases where services ask for config that no longer exist before they have been able
                    // to reconfigure themselves
                    log.log(Level.INFO, TenantRepository.logPre(getId()) +
                                        ": Error resolving instance for builder '" + cacheBuilderClassNameForErrorReport +
                                        "', returning empty config: " + Exceptions.toMessageString(e));
                    payload = ConfigPayload.fromBuilder(new ConfigPayloadBuilder());
                }
                if (def.getCNode() != null)
                    payload.applyDefaultsFromDef(def.getCNode());
                return new Pair<>(payload.toUtf8Array(true), applyOnRestart);
            }
        } catch (Exception e) {
            throw new ConfigurationRuntimeException("Unable to get config for " + app, e);
        }
    }

    private boolean useCache(GetConfigRequest request) {
        return !request.noCache();
    }

    private ConfigDefinition getTargetDef(GetConfigRequest req) {
        ConfigKey<?> configKey = req.getConfigKey();
        DefContent def = req.getDefContent();
        ConfigDefinitionKey configDefinitionKey = new ConfigDefinitionKey(configKey.getName(), configKey.getNamespace());
        if (def.isEmpty()) {
            log.log(Level.FINE, () -> TenantRepository.logPre(getId()) + ("No config schema in request for " + configKey));
            return cache.getDef(configDefinitionKey);
        } else {
            log.log(Level.FINE, () -> TenantRepository.logPre(getId()) + ("Got config schema from request, length:" + def.asList().size() + " : " + configKey));
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
