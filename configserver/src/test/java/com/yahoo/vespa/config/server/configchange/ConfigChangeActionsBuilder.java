// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author geirst
 * @since 5.44
 */
public class ConfigChangeActionsBuilder {

    private final List<ConfigChangeAction> actions = new ArrayList<>();

    private static ServiceInfo createService(String clusterName, String clusterType, String serviceType, String serviceName) {
        return new ServiceInfo(serviceName, serviceType, null,
                ImmutableMap.of("clustername", clusterName, "clustertype", clusterType),
                serviceType + "/" + serviceName, "hostname");
    }

    public ConfigChangeActionsBuilder restart(String message, String clusterName, String clusterType, String serviceType, String serviceName) {
        actions.add(new MockRestartAction(message,
                                          Arrays.asList(createService(clusterName, clusterType, serviceType, serviceName))));
        return this;
    }

    public ConfigChangeActionsBuilder refeed(String name, boolean allowed, String message, String documentType, String clusterName, String serviceName) {
        actions.add(new MockRefeedAction(name,
                                         allowed,
                                         message,
                                         Arrays.asList(createService(clusterName, "myclustertype", "myservicetype", serviceName)), documentType));
        return this;
    }

    public ConfigChangeActions build() {
        return new ConfigChangeActions(actions);
    }
}
