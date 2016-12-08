// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.base.Splitter;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

import java.util.Map;

/**
 * This class is tasked with reading config definitions and legacy configs/ from zookeeper, and create
 * a {@link ServerCache} instance containing these in memory.
 *
 * @author lulf
 * @since 5.1
 */
public class ServerCacheLoader {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ServerCacheLoader.class.getName());
    private final ConfigDefinitionRepo repo;
    private final ConfigCurator configCurator;
    private final Path path;
    public ServerCacheLoader(ConfigCurator configCurator, Path rootPath, ConfigDefinitionRepo repo) {
        this.configCurator = configCurator;
        this.path = rootPath;
        this.repo = repo;
    }

    public ServerCache loadCache() {
        return loadConfigDefinitions();
    }

    /**
     * Reads config definitions from zookeeper, parses them and puts both ConfigDefinition instances
     * and payload (raw config definition) into cache.
     *
     * @return            the populated cache.
     */
    public ServerCache loadConfigDefinitions() {
        ServerCache cache = new ServerCache();
        try {
            log.log(LogLevel.DEBUG, "Getting config definitions");
            loadGlobalConfigDefinitions(cache);
            loadConfigDefinitionsFromPath(cache, path.append(ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH).getAbsolute());
            log.log(LogLevel.DEBUG, "Done getting config definitions");
        } catch (Exception e) {
            throw new IllegalStateException("Could not load config definitions for " + path, e);
        }
        return cache;
    }

    private void loadGlobalConfigDefinitions(ServerCache cache) {
        for (Map.Entry<ConfigDefinitionKey, ConfigDefinition> entry : repo.getConfigDefinitions().entrySet()) {
            cache.addDef(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Loads config definitions from a specified path into server cache and returns it.
     *
     * @param appPath the path to load config definitions from
     */
    private void loadConfigDefinitionsFromPath(ServerCache cache, String appPath) {
        if ( ! configCurator.exists(appPath)) return;
        for (String nodeName : configCurator.getChildren(appPath)) {
            String payload = configCurator.getData(appPath, nodeName);
            ConfigDefinitionKey dKey = ConfigUtils.createConfigDefinitionKeyFromZKString(nodeName);
            cache.addDef(dKey, new ConfigDefinition(dKey.getName(), Splitter.on("\n").splitToList(payload).toArray(new String[0])));
        }
    }

}
