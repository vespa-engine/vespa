// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;

/**
 * Encapsulates the result from preparing an application
 *
 * @author hmusum
 */
public class PrepareResult {

    private final long sessionId;
    private final ConfigChangeActions configChangeActions;
    private final Slime deployLog;

    public PrepareResult(long sessionId, ConfigChangeActions configChangeActions, Slime deployLog) {
        this.sessionId = sessionId;
        this.configChangeActions = configChangeActions;
        this.deployLog = deployLog;
    }

    public long sessionId() {
        return sessionId;
    }

    public ConfigChangeActions configChangeActions() {
        return configChangeActions;
    }

    public Slime deployLog() {
        return deployLog;
    }
}
