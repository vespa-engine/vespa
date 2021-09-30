// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.application.validation.change.VespaReindexAction;
import com.yahoo.vespa.model.application.validation.change.VespaRestartAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * @author geirst
 */
public class ConfigChangeActionsBuilder {

    private final List<ConfigChangeAction> actions = new ArrayList<>();

    private static ServiceInfo createService(String clusterName, String clusterType, String serviceType, String serviceName) {
        return new ServiceInfo(serviceName, serviceType, null,
                ImmutableMap.of("clustername", clusterName, "clustertype", clusterType),
                serviceType + "/" + serviceName, "hostname");
    }

    public ConfigChangeActionsBuilder restart(String message, String clusterName, String clusterType, String serviceType, String serviceName) {
        return restart(message, clusterName, clusterType, serviceType, serviceName, false);
    }

    public ConfigChangeActionsBuilder restart(String message, String clusterName, String clusterType, String serviceType, String serviceName, boolean ignoreForInternalRedeploy) {
        actions.add(new VespaRestartAction(ClusterSpec.Id.from(clusterName),
                                           message,
                                           createService(clusterName, clusterType, serviceType, serviceName),
                                           ignoreForInternalRedeploy));
        return this;
    }


    ConfigChangeActionsBuilder refeed(ValidationId validationId, String message, String documentType, String clusterName, String serviceName) {
        actions.add(new MockRefeedAction(validationId,
                                         message,
                                         List.of(createService(clusterName, "myclustertype", "myservicetype", serviceName)), documentType));
        return this;
    }

    ConfigChangeActionsBuilder reindex(ValidationId validationId, String message, String documentType, String clusterName, String serviceName) {
        List<ServiceInfo> services = List.of(createService(clusterName, "myclustertype", "myservicetype", serviceName));
        actions.add(VespaReindexAction.of(ClusterSpec.Id.from(clusterName), validationId, message, services, documentType));
        return this;
    }

    public ConfigChangeActions build() {
        return new ConfigChangeActions(actions);
    }
}
