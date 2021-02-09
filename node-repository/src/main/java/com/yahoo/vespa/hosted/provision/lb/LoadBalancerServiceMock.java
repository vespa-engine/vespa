// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;

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

    public Map<LoadBalancerId, LoadBalancerInstance> instances() {
        return Collections.unmodifiableMap(instances);
    }

    public LoadBalancerServiceMock throwOnCreate(boolean throwOnCreate) {
        this.throwOnCreate = throwOnCreate;
        return this;
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
                HostName.from("lb-" + spec.application().toShortString() + "-" + spec.cluster().value()),
                Optional.of(new DnsZone("zone-id-1")),
                Collections.singleton(4443),
                ImmutableSet.of("10.2.3.0/24", "10.4.5.0/24"),
                spec.reals());
        instances.put(id, instance);
        return instance;
    }

    @Override
    public void remove(ApplicationId application, ClusterSpec.Id cluster) {
        instances.remove(new LoadBalancerId(application, cluster));
    }

}
