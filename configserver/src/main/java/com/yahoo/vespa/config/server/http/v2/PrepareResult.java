// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;

/**
 * Encapsulates the result from preparing an application
 *
 * @author hmusum
 */
public class PrepareResult {

    private final long sessionId;
    private final ConfigChangeActions configChangeActions;
    private final DeployHandlerLogger logger;

    public PrepareResult(long sessionId, ConfigChangeActions configChangeActions, DeployHandlerLogger logger) {
        this.sessionId = sessionId;
        this.configChangeActions = configChangeActions;
        this.logger = logger;
    }

    public long sessionId() {
        return sessionId;
    }

    public ConfigChangeActions configChangeActions() {
        return configChangeActions;
    }

    public DeployHandlerLogger deployLogger() {
        return logger;
    }

}
