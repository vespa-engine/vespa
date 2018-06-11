// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.protocol.ConfigResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache that holds configs and config definitions (builtin and user config definitions).
 *
 * @author vegardh
 */
public class ServerCache {

    private final ConfigDefinitionRepo builtinConfigDefinitions;
    private final ConfigDefinitionRepo userConfigDefinitions;

    // NOTE: The reason we do a double mapping here is to dedup configs that have the same md5.
    private final Map<ConfigCacheKey, String> md5Sums = new ConcurrentHashMap<>();
    private final Map<String, ConfigResponse> md5ToConfig = new ConcurrentHashMap<>();


    public ServerCache(ConfigDefinitionRepo builtinConfigDefinitions, ConfigDefinitionRepo userConfigDefinitions) {
        this.builtinConfigDefinitions = builtinConfigDefinitions;
        this.userConfigDefinitions = userConfigDefinitions;
    }

    // For testing only
    public ServerCache() {
        this(new StaticConfigDefinitionRepo(), new UserConfigDefinitionRepo());
    }

    public void put(ConfigCacheKey key, ConfigResponse config, String configMd5) {
        md5Sums.put(key, configMd5);
        md5ToConfig.put(configMd5, config);
    }

    public ConfigResponse get(ConfigCacheKey key) {
        String md5 = md5Sums.get(key);
        if (md5 == null) return null;
        return md5ToConfig.get(md5);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cache\n");
        sb.append("builtin defs: ").append(builtinConfigDefinitions.getConfigDefinitions().size()).append("\n");
        sb.append("user defs:    ").append(userConfigDefinitions.getConfigDefinitions().size()).append("\n");
        sb.append("md5sums:      ").append(md5Sums.size()).append("\n");
        sb.append("md5ToConfig:  ").append(md5ToConfig.size()).append("\n");

        return sb.toString();
    }

    public ConfigDefinition getDef(ConfigDefinitionKey defKey) {
        ConfigDefinition def = userConfigDefinitions.get(defKey);
        return (def != null) ? def : builtinConfigDefinitions.getConfigDefinitions().get(defKey);
    }
    
    /**
     * The number of different {@link ConfigResponse} elements
     * @return elems
     */
    public int configElems() {
        return md5ToConfig.size();
    }
    
    /**
     * The number of different key→checksum mappings
     * @return elems
     */
    public int checkSumElems() {
        return md5Sums.size();
    }

}
