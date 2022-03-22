// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;

import java.util.Optional;

/**
 * Injectable component that provides provision service for load-balancers and hosts
 *
 * @author freva
 */
public interface ProvisionServiceProvider {

    Optional<LoadBalancerService> getLoadBalancerService();

    Optional<HostProvisioner> getHostProvisioner();

    HostResourcesCalculator getHostResourcesCalculator();
}
