// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;

import java.util.Optional;

/**
 * @author freva
 */
public class EmptyProvisionServiceProvider implements ProvisionServiceProvider {

    private final HostResourcesCalculator hostResourcesCalculator = new IdentityHostResourcesCalculator();

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

    private static class IdentityHostResourcesCalculator implements HostResourcesCalculator {

        @Override
        public NodeResources realResourcesOf(Nodelike node, NodeRepository repository) { return node.resources(); }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) { return flavor.resources(); }

        @Override
        public NodeResources requestToReal(NodeResources resources, boolean exclusive, boolean bestCase) { return resources; }

        @Override
        public NodeResources realToRequest(NodeResources resources, boolean exclusive, boolean bestCase) { return resources; }

        @Override
        public long reservedDiskSpaceInBase2Gb(NodeType nodeType, boolean sharedHost) { return 0; }

    }

}
