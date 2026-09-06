// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.ConfigDefinitionStore;
import com.yahoo.config.model.producer.ConfigOverrides;
import com.yahoo.text.XML;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.builder.xml.dom.DomConfigPayloadBuilder;
import org.w3c.dom.Element;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ulf Lilleengen
 */
public class ConfigOverrideBuilder {

    public static final Logger log = Logger.getLogger(ConfigOverrideBuilder.class.getPackage().toString());

    public static ConfigOverrides build(Element producerSpec, ConfigDefinitionStore configDefinitionStore, DeployLogger deployLogger) {
        Map<ConfigDefinitionKey, ConfigPayloadBuilder> builderMap = new LinkedHashMap<>();
        log.log(Level.FINE, () -> "getConfigOverrides for " + producerSpec);
        for (Element configE : XML.getChildren(producerSpec, "config")) {
            buildElement(configE, builderMap, configDefinitionStore, deployLogger);
        }
        return new ConfigOverrides(builderMap);
    }


    private static void buildElement(Element element, Map<ConfigDefinitionKey, ConfigPayloadBuilder> builderMap,
                                     ConfigDefinitionStore configDefinitionStore, DeployLogger logger) {
        ConfigDefinitionKey key = DomConfigPayloadBuilder.parseConfigName(element);

        // TODO: Change to WARNING, starting with INFO for now to avoid too much noise
        logger.logApplicationPackage(Level.INFO, "Config override for '" + key + "' found. Config " +
                                     "overrides are discouraged, an override may break deployment in a coming " +
                                     "version and might have no or unintended effect");

        Optional<ConfigDefinition> def = configDefinitionStore.getConfigDefinition(key);
        if (def.isEmpty()) { // TODO: Fail instead of warn
            logger.logApplicationPackage(Level.WARNING, "Unable to find config definition '" + key.asFileName() +
                                         "'. Please ensure that the name is spelled correctly, and that the def file is included in a bundle.");
        }
        ConfigPayloadBuilder payloadBuilder = new DomConfigPayloadBuilder(def.orElse(null)).build(element);
        ConfigPayloadBuilder old = builderMap.get(key);
        if (old != null) {
            logger.logApplicationPackage(Level.WARNING, "Multiple overrides for " + key + " found. Applying in the order they are discovered");
            old.override(payloadBuilder);
        } else {
            builderMap.put(key, payloadBuilder);
        }
    }

}

