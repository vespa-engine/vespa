// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;

/**
 * @author hakon
 */
public class StateImpl implements State {
    private final ConfigServerApi configServerApi;

    public StateImpl(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public HealthResponse getHealth() {
        return configServerApi.get("/state/v1/health", HealthResponse.class);
    }
}
