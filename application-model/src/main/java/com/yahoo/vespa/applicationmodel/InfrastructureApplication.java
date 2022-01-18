// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;

import java.util.List;
import java.util.stream.Stream;

/**
 * Special infrastructure applications in hosted Vespa.
 *
 * @author hakonhall
 */
public enum InfrastructureApplication {
    CONTROLLER_HOST("controller-host", NodeType.controllerhost),
    CONTROLLER("controller", NodeType.controller),
    CONFIG_SERVER_HOST("configserver-host", NodeType.confighost),
    CONFIG_SERVER("zone-config-servers", NodeType.config),
    PROXY_HOST("proxy-host", NodeType.proxyhost),
    PROXY("routing", NodeType.proxy),
    TENANT_HOST("tenant-host", NodeType.host);

    private final ApplicationId id;
    private final NodeType nodeType;

    /** Returns all applications that MAY be encountered in hosted Vespa, e.g. not DEV_HOST. */
    public static List<InfrastructureApplication> toList() {
        return List.of(values());
    }

    public static InfrastructureApplication withNodeType(NodeType nodeType) {
        return Stream.of(values())
                     .filter(application -> nodeType == application.nodeType)
                     .findAny()
                     .orElseThrow(() -> new IllegalArgumentException("No application associated with " + nodeType));
    }

    InfrastructureApplication(String name, NodeType nodeType) {
        this.id = ApplicationId.from(TenantId.HOSTED_VESPA.value(), name, "default");
        this.nodeType = nodeType;
    }

    public ApplicationId id() { return id; }
    /** Avoid using {@link #name()} which is the name of the enum constant. */
    public String applicationName() { return id.application().value(); }
    public NodeType nodeType() { return nodeType; }
    public boolean isConfigServerLike() { return this == CONFIG_SERVER || this == CONTROLLER; }
    public boolean isConfigServerHostLike() { return this == CONFIG_SERVER_HOST || this == CONTROLLER_HOST; }

    @Override
    public String toString() {
        return "InfrastructureApplication{" +
               "id=" + id +
               ", nodeType=" + nodeType +
               '}';
    }
}
