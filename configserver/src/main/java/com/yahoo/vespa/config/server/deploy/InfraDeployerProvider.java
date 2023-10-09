// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.provision.InfraDeployer;

import java.util.Optional;

/**
 * This class is necessary to support both having and not having an infrastructure deployer. We inject
 * a component registry here, which then enables us to check whether or not we have an infrastructure
 * deployer available. We only have an infrastructure deployer if we are running in hosted mode.
 *
 * @author freva
 */
public class InfraDeployerProvider {

    private final Optional<InfraDeployer> infraDeployer;

    public InfraDeployerProvider(ComponentRegistry<InfraDeployer> infraDeployerRegistry, ConfigserverConfig configserverConfig) {
        if (infraDeployerRegistry.allComponents().isEmpty() || ! configserverConfig.hostedVespa()) {
            infraDeployer = Optional.empty();
        } else {
            infraDeployer = Optional.of(infraDeployerRegistry.allComponents().get(0));
        }
    }

    private InfraDeployerProvider(ComponentRegistry<InfraDeployer> componentRegistry) {
        this(componentRegistry, new ConfigserverConfig(new ConfigserverConfig.Builder()));
    }

    /** Returns the infrastructure deployer, or empty if we are not in hosted mode */
    public Optional<InfraDeployer> getInfraDeployer() {
        return infraDeployer;
    }

    // for testing
    public static InfraDeployerProvider empty() {
        return new InfraDeployerProvider(new ComponentRegistry<>());
    }

    // for testing
    public static InfraDeployerProvider withInfraDeployer(InfraDeployer infraDeployer) {
        ComponentRegistry<InfraDeployer> registry = new ComponentRegistry<>();
        registry.register(ComponentId.createAnonymousComponentId("foobar"), infraDeployer);
        return new InfraDeployerProvider(registry, new ConfigserverConfig(new ConfigserverConfig.Builder().hostedVespa(true)));
    }

    /** Creates either an empty provider or a provider having the given infrastructure deployer */
    public static InfraDeployerProvider from(Optional<InfraDeployer> infraDeployer) {
        return infraDeployer.map(InfraDeployerProvider::withInfraDeployer).orElseGet(InfraDeployerProvider::empty);
    }

}
