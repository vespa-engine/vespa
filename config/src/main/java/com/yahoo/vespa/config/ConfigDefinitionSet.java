// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.CNode;
import com.yahoo.log.LogLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to hold config definitions and resolving requests for the correct definition
 *
 * @author hmusum
 * @since 5.1
 */
public class ConfigDefinitionSet {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigDefinitionSet.class.getName());

    private final Map<ConfigDefinitionKey, ConfigDefinition> defs = new ConcurrentHashMap<ConfigDefinitionKey, ConfigDefinition>();

    public ConfigDefinitionSet() {

    }

    public void add(ConfigDefinitionKey key, ConfigDefinition def) {
        log.log(LogLevel.DEBUG, "Adding to set: " + key);
        defs.put(key, def);
    }

    /**
     * Returns a ConfigDefinition from the set matching the given <code>key</code>. If no ConfigDefinition
     * is found in the set, it will try to find a ConfigDefinition with same name in the default namespace.
     * @param key a {@link ConfigDefinitionKey}
     * @return a ConfigDefinition if found, else null
     */
    public ConfigDefinition get(ConfigDefinitionKey key) {
        log.log(LogLevel.DEBUG, "Getting from set " + defs + " for key " + key);
        ConfigDefinition ret = defs.get(key);
        if (ret == null) {
            // Return entry if we fallback to default namespace
            log.log(LogLevel.DEBUG, "Found no def for key " + key + ", trying to find def with same name in default namespace");
            for (Map.Entry<ConfigDefinitionKey, ConfigDefinition> entry : defs.entrySet()) {
                if (key.getName().equals(entry.getKey().getName()) && entry.getKey().getNamespace().equals(CNode.DEFAULT_NAMESPACE)) {
                    return entry.getValue();
                }
            }
        }
        return ret;
    }

    public int size() {
        return defs.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ConfigDefinitionKey key : defs.keySet()) {
                sb.append(key.toString()).append("\n");
        }
        return sb.toString();
    }

}
