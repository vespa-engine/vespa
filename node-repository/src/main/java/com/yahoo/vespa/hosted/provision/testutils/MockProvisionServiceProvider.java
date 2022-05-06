// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerServiceMock;
import com.yahoo.vespa.hosted.provision.provisioning.EmptyProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionServiceProvider;

import java.util.Optional;

/**
 * @author freva
 */
public class MockProvisionServiceProvider implements ProvisionServiceProvider {

    private final Optional<LoadBalancerService> loadBalancerService;
    private final Optional<HostProvisioner> hostProvisioner;
    private final HostResourcesCalculator hostResourcesCalculator;

    @Inject
    public MockProvisionServiceProvider() {
        this(new LoadBalancerServiceMock(), null);
    }

    public MockProvisionServiceProvider(LoadBalancerService loadBalancerService, HostProvisioner hostProvisioner) {
        this(loadBalancerService, hostProvisioner, new EmptyProvisionServiceProvider().getHostResourcesCalculator());
    }

    public MockProvisionServiceProvider(LoadBalancerService loadBalancerService, HostProvisioner hostProvisioner,
                                        HostResourcesCalculator hostResourcesCalculator) {
        this.loadBalancerService = Optional.ofNullable(loadBalancerService);
        this.hostProvisioner = Optional.ofNullable(hostProvisioner);
        this.hostResourcesCalculator = hostResourcesCalculator;
    }

    @Override
    public Optional<LoadBalancerService> getLoadBalancerService() {
        return loadBalancerService;
    }

    @Override
    public Optional<HostProvisioner> getHostProvisioner() {
        return hostProvisioner;
    }

    @Override
    public HostResourcesCalculator getHostResourcesCalculator() {
        return hostResourcesCalculator;
    }
}
