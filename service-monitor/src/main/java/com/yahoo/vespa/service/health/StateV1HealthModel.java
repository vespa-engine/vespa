// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.service.executor.RunletExecutor;
import com.yahoo.vespa.service.model.ApplicationInstanceGenerator;
import com.yahoo.vespa.service.monitor.ServiceId;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
public class StateV1HealthModel implements AutoCloseable {
    private static final String PORT_TAG_STATE = "STATE";
    private static final String PORT_TAG_HTTP = "HTTP";

    /** Port tags implying /state/v1/health is served on HTTP. */
    public static final List<String> HTTP_HEALTH_PORT_TAGS = List.of(PORT_TAG_HTTP, PORT_TAG_STATE);
    private final Duration targetHealthStaleness;
    private final Duration requestTimeout;
    private final Duration connectionKeepAlive;
    private final RunletExecutor executor;

    StateV1HealthModel(Duration targetHealthStaleness,
                       Duration requestTimeout,
                       Duration connectionKeepAlive,
                       RunletExecutor executor) {
        this.targetHealthStaleness = targetHealthStaleness;
        this.requestTimeout = requestTimeout;
        this.connectionKeepAlive = connectionKeepAlive;
        this.executor = executor;
    }

    Map<ServiceId, HealthEndpoint> extractHealthEndpoints(ApplicationInfo application) {
        Map<ServiceId, HealthEndpoint> endpoints = new HashMap<>();

        for (HostInfo hostInfo : application.getModel().getHosts()) {
            HostName hostname = HostName.of(hostInfo.getHostname());
            for (ServiceInfo serviceInfo : hostInfo.getServices()) {
                ServiceId serviceId = ApplicationInstanceGenerator.getServiceId(application, serviceInfo);
                for (PortInfo portInfo : serviceInfo.getPorts()) {
                    if (portTaggedWith(portInfo, HTTP_HEALTH_PORT_TAGS)) {
                        StateV1HealthEndpoint endpoint = new StateV1HealthEndpoint(
                                serviceId,
                                hostname,
                                portInfo.getPort(),
                                targetHealthStaleness,
                                requestTimeout,
                                connectionKeepAlive,
                                executor);
                        endpoints.put(serviceId, endpoint);
                        break; // Avoid >1 endpoints per serviceId
                    }
                }
            }
        }

        return endpoints;
    }

    static boolean portTaggedWith(PortInfo portInfo, List<String> requiredTags) {
        // vespa-model-inspect displays upper case tags, while actual tags for (at least) node-admin are lower case.
        Collection<String> upperCasePortTags = portInfo.getTags().stream().map(String::toUpperCase).collect(Collectors.toSet());
        for (var tag : requiredTags) {
            if (!upperCasePortTags.contains(tag.toUpperCase())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void close() {
        executor.close();
    }
}
