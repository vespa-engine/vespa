// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeRestartAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.List;

/**
 * @author geirst
 * @since 5.44
 */
public class MockRestartAction extends MockConfigChangeAction implements ConfigChangeRestartAction {
    public MockRestartAction(String message, List<ServiceInfo> services) {
        super(message, services);
    }
}
