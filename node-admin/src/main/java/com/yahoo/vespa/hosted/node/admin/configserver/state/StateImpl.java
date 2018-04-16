// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.state.bindings.HealthResponse;

/**
 * @author hakon
 */
public class StateImpl implements State {
    private final ConfigServerApi configServerApi;

    public StateImpl(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public HealthCode getHealth() {
        HealthResponse response;
        try {
            response = configServerApi.get("/state/v1/health", HealthResponse.class);
        } catch (RuntimeException e) {
            if (causedByConnectionRefused(e)) {
                return HealthCode.DOWN;
            }

            throw e;
        }
        return HealthCode.fromString(response.status.code);
    }

    private static boolean causedByConnectionRefused(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.ConnectException) {
                return true;
            }
        }

        return false;
    }
}
