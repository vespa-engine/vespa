// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.base.Splitter;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.curator.Curator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
public class UserConfigDefinitionRepo implements ConfigDefinitionRepo {
    private static final Logger log = Logger.getLogger(UserConfigDefinitionRepo.class.getName());

    private final Map<ConfigDefinitionKey, ConfigDefinition> defs = new LinkedHashMap<>();

    // For testing only
    public UserConfigDefinitionRepo() {}

    public UserConfigDefinitionRepo(Curator curator, Path appPath) {
        if (curator.exists(appPath)) {
            for (String nodeName : curator.getChildren(appPath)) {
                String payload = curator.getData(appPath.append(nodeName))
                                        .map(Utf8::toString)
                                        .orElseThrow(() -> new IllegalArgumentException("No config definition data at " + nodeName));
                ConfigDefinitionKey dKey = ConfigUtils.createConfigDefinitionKeyFromZKString(nodeName);
                defs.put(dKey, new ConfigDefinition(dKey.getName(), Splitter.on("\n").splitToList(payload).toArray(new String[0])));
            }
        } else {
            log.log(Level.WARNING, "Path " + appPath + " does not exist, not able to load add user config definitions");
        }
    }

    public void add(ConfigDefinitionKey key, ConfigDefinition configDefinition) {
        defs.put(key, configDefinition);
    }

    @Override
    public Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions() {
        return defs;
    }

    @Override
    public ConfigDefinition get(ConfigDefinitionKey key) {
        return defs.get(key);
    }
}
