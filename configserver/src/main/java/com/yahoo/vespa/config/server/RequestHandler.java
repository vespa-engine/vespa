// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import java.util.Optional;
import java.util.Set;

import com.yahoo.config.provision.Version;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.protocol.ConfigResponse;

/**
 * Instances of this can serve misc config related requests
 * 
 * @author lulf
 * @since 5.1
 */
public interface RequestHandler {

    /**
     * Resolves a config. Mandatory subclass hook for getConfig().
     * @param appId The application id to use
     * @param req a config request
     * @param vespaVersion vespa version
     * @return The resolved config if it exists, else null.
     */
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion);

    /**
     * Lists all configs (name, configKey) in the config model.
     * @param appId application id to use
     * @param vespaVersion optional vespa version
     * @param recursive If true descend into all levels
     * @return set of keys
     */
    public Set<ConfigKey<?>> listConfigs(ApplicationId appId, Optional<Version> vespaVersion, boolean recursive);
    
    /**
     * Lists all configs (name, configKey) of the given key. The config id of the key is interpreted as a prefix to match.
     * @param appId application id to use
     * @param vespaVersion optional vespa version
     * @param key def key to match
     * @param recursive If true descend into all levels
     * @return set of keys
     */
    public Set<ConfigKey<?>> listNamedConfigs(ApplicationId appId, Optional<Version> vespaVersion, ConfigKey<?> key, boolean recursive);

    /**
     * Lists all available configs produced
     * @param appId application id to use
     * @param vespaVersion optional vespa version
     * @return set of keys
     */
    public Set<ConfigKey<?>> allConfigsProduced(ApplicationId appId, Optional<Version> vespaVersion);
    
    /**
     * List all config ids present
     * @param appId application id to use
     * @param vespaVersion optional vespa version
     * @return a Set containing all config ids available
     */
    public Set<String> allConfigIds(ApplicationId appId, Optional<Version> vespaVersion);

    /**
     * True if application loaded
     * @param appId The application id to use
     * @param vespaVersion optional vespa version
     * @return true if app loaded
     */
    boolean hasApplication(ApplicationId appId, Optional<Version> vespaVersion);

    /**
     * Resolve {@link ApplicationId} for a given hostname. Returns a default {@link ApplicationId} if no applications
     * are found for that host.
     *
     * @param hostName hostname of client.
     * @return an {@link ApplicationId} instance.
     */
    ApplicationId resolveApplicationId(String hostName);
}
