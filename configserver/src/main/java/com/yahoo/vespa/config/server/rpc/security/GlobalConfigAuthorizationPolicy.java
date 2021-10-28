// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Specifies which node type that are allowed to access global configuration
 *
 * @author bjorncs
 */
enum GlobalConfigAuthorizationPolicy {

    LB_SERVICES(new LbServicesConfig.Builder(), NodeType.proxy);

    final String namespace;
    final String name;
    final EnumSet<NodeType> allowedToAccess;

    GlobalConfigAuthorizationPolicy(ConfigInstance.Builder builder, NodeType... allowedToAccess) {
        this.namespace = builder.getDefNamespace();
        this.name = builder.getDefName();
        this.allowedToAccess = EnumSet.copyOf(List.of(allowedToAccess));
    }

    static void verifyAccessAllowed(ConfigKey<?> configKey, NodeType nodeType) {
        GlobalConfigAuthorizationPolicy policy = findPolicyFromConfigKey(configKey);
        if (!policy.allowedToAccess.contains(nodeType)) {
            String message = String.format(
                    "Node with type '%s' is not allowed to access global config [%s]",
                    nodeType, configKey);
            throw new AuthorizationException(message);
        }
    }

    private static GlobalConfigAuthorizationPolicy findPolicyFromConfigKey(ConfigKey<?> configKey) {
        return Arrays.stream(values())
                .filter(policy -> policy.namespace.equals(configKey.getNamespace()) && policy.name.equals(configKey.getName()))
                .findAny()
                .orElseThrow(() -> new AuthorizationException(String.format("No policy defined for global config [%s]", configKey)));
    }

}

