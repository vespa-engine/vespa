// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author mpolden
 */
public class LoadBalancerServiceMock implements LoadBalancerService {

    private final Map<LoadBalancerId, LoadBalancerInstance> instances = new HashMap<>();
    private boolean throwOnCreate = false;
    private boolean supportsProvisioning = true;

    public Map<LoadBalancerId, LoadBalancerInstance> instances() {
        return Collections.unmodifiableMap(instances);
    }

    public LoadBalancerServiceMock throwOnCreate(boolean throwOnCreate) {
        this.throwOnCreate = throwOnCreate;
        return this;
    }

    public LoadBalancerServiceMock supportsProvisioning(boolean supportsProvisioning) {
        this.supportsProvisioning = supportsProvisioning;
        return this;
    }

    @Override
    public boolean supports(NodeType nodeType, ClusterSpec.Type clusterType) {
        if (!supportsProvisioning) return false;
        return (nodeType == NodeType.tenant && clusterType.isContainer()) ||
               nodeType.isConfigServerLike();
    }

    @Override
    public Protocol protocol() {
        return Protocol.ipv4;
    }

    @Override
    public LoadBalancerInstance create(LoadBalancerSpec spec, boolean force) {
        if (throwOnCreate) throw new IllegalStateException("Did not expect a new load balancer to be created");
        var id = new LoadBalancerId(spec.application(), spec.cluster());
        var oldInstance = instances.get(id);
        if (!force && oldInstance != null && !oldInstance.reals().isEmpty() && spec.reals().isEmpty()) {
            throw new IllegalArgumentException("Refusing to remove all reals from load balancer " + id);
        }
        var instance = new LoadBalancerInstance(
                Optional.of(DomainName.of("lb-" + spec.application().toShortString() + "-" + spec.cluster().value())),
                Optional.empty(),
                Optional.of(new DnsZone("zone-id-1")),
                Collections.singleton(4443),
                ImmutableSet.of("10.2.3.0/24", "10.4.5.0/24"),
                spec.reals(),
                spec.cloudAccount());
        instances.put(id, instance);
        return instance;
    }

    @Override
    public void remove(LoadBalancer loadBalancer) {
        instances.remove(loadBalancer.id());
    }

}
