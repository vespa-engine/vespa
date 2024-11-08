// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.hosted.provision.backup.SnapshotStoreMock;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerServiceMock;
import com.yahoo.vespa.hosted.provision.provisioning.EmptyProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.provisioning.SnapshotStore;

import java.util.Optional;

/**
 * @author freva
 */
public class MockProvisionServiceProvider implements ProvisionServiceProvider {

    private final Optional<LoadBalancerService> loadBalancerService;
    private final Optional<HostProvisioner> hostProvisioner;
    private final HostResourcesCalculator hostResourcesCalculator;
    private final Optional<SnapshotStore> snapshotStore;

    @Inject
    public MockProvisionServiceProvider() {
        this(new LoadBalancerServiceMock(), null);
    }

    public MockProvisionServiceProvider(LoadBalancerService loadBalancerService, HostProvisioner hostProvisioner) {
        this(loadBalancerService, hostProvisioner, new EmptyProvisionServiceProvider().getHostResourcesCalculator(), new SnapshotStoreMock());
    }

    public MockProvisionServiceProvider(LoadBalancerService loadBalancerService, HostProvisioner hostProvisioner,
                                        HostResourcesCalculator hostResourcesCalculator, SnapshotStore snapshotStore) {
        this.loadBalancerService = Optional.ofNullable(loadBalancerService);
        this.hostProvisioner = Optional.ofNullable(hostProvisioner);
        this.hostResourcesCalculator = hostResourcesCalculator;
        this.snapshotStore = Optional.ofNullable(snapshotStore);
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
    public Optional<SnapshotStore> getSnapshotStore() {
        return snapshotStore;
    }

    @Override
    public HostResourcesCalculator getHostResourcesCalculator() {
        return hostResourcesCalculator;
    }

}
