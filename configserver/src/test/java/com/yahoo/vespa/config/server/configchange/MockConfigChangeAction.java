// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.List;

/**
 * @author geirst
 * @since 5.44
 */
public abstract class MockConfigChangeAction implements ConfigChangeAction {

    private final String message;
    private final List<ServiceInfo> services;

    MockConfigChangeAction(String message, List<ServiceInfo> services) {
        this.message = message;
        this.services = services;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<ServiceInfo> getServices() {
        return services;
    }
}
