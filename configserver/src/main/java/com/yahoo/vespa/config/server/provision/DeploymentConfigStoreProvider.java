// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.provision;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.provision.DeploymentConfigStore;

import java.util.Optional;

/**
 * Provides an optional {@link DeploymentConfigStore}. A store is only present when running in hosted mode
 * and a concrete implementation has been registered as a component.
 *
 * @author olaa
 */
public class DeploymentConfigStoreProvider {

    private final Optional<DeploymentConfigStore> store;

    public DeploymentConfigStoreProvider(ComponentRegistry<DeploymentConfigStore> registry, ConfigserverConfig configserverConfig) {
        if (registry.allComponents().isEmpty() || ! configserverConfig.hostedVespa()) {
            store = Optional.empty();
        } else {
            store = Optional.of(registry.allComponents().get(0));
        }
    }

    /** Returns the deployment config store, or empty if not in hosted mode */
    public Optional<DeploymentConfigStore> getStore() {
        return store;
    }

}
