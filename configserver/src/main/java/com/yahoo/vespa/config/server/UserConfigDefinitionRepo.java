// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.base.Splitter;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import java.util.logging.Level;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
public class UserConfigDefinitionRepo implements ConfigDefinitionRepo {
    private static final Logger log = Logger.getLogger(UserConfigDefinitionRepo.class.getName());

    private final Map<ConfigDefinitionKey, ConfigDefinition> defs = new LinkedHashMap<>();

    // For testing only
    public UserConfigDefinitionRepo() {}

    public UserConfigDefinitionRepo(ConfigCurator configCurator, String appPath) {
        if (configCurator.exists(appPath)) {
            for (String nodeName : configCurator.getChildren(appPath)) {
                String payload = configCurator.getData(appPath, nodeName);
                ConfigDefinitionKey dKey = ConfigUtils.createConfigDefinitionKeyFromZKString(nodeName);
                defs.put(dKey, new ConfigDefinition(dKey.getName(), Splitter.on("\n").splitToList(payload).toArray(new String[0])));
            }
        } else {
            log.log(LogLevel.WARNING, "Path " + appPath + " does not exist, not able to load add user config definitions");
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
