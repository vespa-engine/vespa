// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;

import java.util.Optional;

/**
 * @author freva
 */
public class EmptyProvisionServiceProvider implements ProvisionServiceProvider {
    private final HostResourcesCalculator hostResourcesCalculator = new NoopHostResourcesCalculator();

    @Override
    public Optional<LoadBalancerService> getLoadBalancerService() {
        return Optional.empty();
    }

    @Override
    public Optional<HostProvisioner> getHostProvisioner() {
        return Optional.empty();
    }

    @Override
    public HostResourcesCalculator getHostResourcesCalculator() {
        return hostResourcesCalculator;
    }

    public static class NoopHostResourcesCalculator implements HostResourcesCalculator {

        @Override
        public NodeResources availableCapacityOf(String flavorName, NodeResources hostResources) {
            return hostResources;
        }
    }
}
