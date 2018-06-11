// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.base.Splitter;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.UserConfigDefinitionRepo;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

/**
 * Loads config definitions from zookeeper and creates a {@link ServerCache} instance containing these
 * and the builtin config definitions in memory.
 *
 * @author Ulf Lilleengen
 */
public class ServerCacheLoader {

    private final ConfigDefinitionRepo repo;
    private final ConfigCurator configCurator;
    private final Path path;

    ServerCacheLoader(ConfigCurator configCurator, Path rootPath, ConfigDefinitionRepo builtinConfigDefinitions) {
        this.configCurator = configCurator;
        this.path = rootPath;
        this.repo = builtinConfigDefinitions;
    }

    public ServerCache loadCache() {
        return loadConfigDefinitions();
    }

    /**
     * Reads config definitions from zookeeper, parses them and puts both ConfigDefinition instances
     * and payload (raw config definition) into cache.
     *
     * @return the populated cache.
     */
    private ServerCache loadConfigDefinitions() {
        try {
            return new ServerCache(repo, createUserConfigDefinitionsRepo(path.append(ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH).getAbsolute()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load user config definitions from " + path, e);
        }
    }

    /**
     * Creates repo with user config definitions
     *
     * @param appPath the path to load config definitions from
     */
    private UserConfigDefinitionRepo createUserConfigDefinitionsRepo(String appPath) {
        UserConfigDefinitionRepo userConfigDefinitionRepo = new UserConfigDefinitionRepo();
        if ( ! configCurator.exists(appPath)) return userConfigDefinitionRepo;

        for (String nodeName : configCurator.getChildren(appPath)) {
            String payload = configCurator.getData(appPath, nodeName);
            ConfigDefinitionKey dKey = ConfigUtils.createConfigDefinitionKeyFromZKString(nodeName);
            userConfigDefinitionRepo.add(dKey, new ConfigDefinition(dKey.getName(), Splitter.on("\n").splitToList(payload).toArray(new String[0])));
        }
        return userConfigDefinitionRepo;
    }

}
