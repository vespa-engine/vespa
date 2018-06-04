// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.server.model.SuperModelConfigProvider;
import com.yahoo.vespa.config.server.rpc.ConfigResponseFactory;

import java.io.IOException;
import java.io.StringReader;

/**
 * Handler for global configs that must be resolved using the global SuperModel instance. Deals with
 * reloading of config as well.
 *
 * @author lulf
 * @since 5.9
 */
public class SuperModelController {

    private final SuperModelConfigProvider model;
    private final long generation;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final ConfigResponseFactory responseFactory;

    public SuperModelController(SuperModelConfigProvider model, ConfigDefinitionRepo configDefinitionRepo, long generation, ConfigResponseFactory responseFactory) {
        this.model = model;
        this.configDefinitionRepo = configDefinitionRepo;
        this.generation = generation;
        this.responseFactory = responseFactory;
    }

    /**
     * Resolves global config for given request.
     *
     * @param request The {@link com.yahoo.vespa.config.GetConfigRequest} to find config for.
     * @return a {@link com.yahoo.vespa.config.protocol.ConfigResponse} containing the response for this request.
     * @throws java.lang.IllegalArgumentException if no such config was found.
     */
    public ConfigResponse resolveConfig(GetConfigRequest request) {
        ConfigKey<?> configKey = request.getConfigKey();
        InnerCNode targetDef = getConfigDefinition(request.getConfigKey(), request.getDefContent());
        ConfigPayload payload = model.getConfig(configKey);
        return responseFactory.createResponse(payload, targetDef, generation, false);
    }

    private InnerCNode getConfigDefinition(ConfigKey<?> configKey, DefContent defContent) {
        if (defContent.isEmpty()) {
            ConfigDefinitionKey configDefinitionKey = new ConfigDefinitionKey(configKey.getName(), configKey.getNamespace());
            ConfigDefinition configDefinition = configDefinitionRepo.getConfigDefinitions().get(configDefinitionKey);
            if (configDefinition == null) {
                throw new UnknownConfigDefinitionException("Unable to find config definition for '" + configKey.getNamespace() + "." + configKey.getName());
            }
            return configDefinition.getCNode();
        } else {
            DefParser dParser = new DefParser(configKey.getName(), new StringReader(defContent.asString()));
            return dParser.getTree();
        }
    }

    public SuperModelConfigProvider getSuperModel() {
        return model;
    }

    long getGeneration() { return generation; }

    public <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> configClass, ApplicationId applicationId, String configId) throws IOException {
        return model.getConfig(configClass, applicationId, configId);
    }

}
