// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.EndpointsChecker.Availability;
import com.yahoo.config.provision.EndpointsChecker.Endpoint;
import com.yahoo.config.provision.NodeType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

/**
 * @author mpolden
 */
public class LoadBalancerServiceMock implements LoadBalancerService {

    private record Key(ApplicationId application, ClusterSpec.Id cluster, String idSeed) {
        @Override public int hashCode() { return idSeed == null ? Objects.hash(application, cluster) : Objects.hash(idSeed); }
        @Override public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Key key)) return false;
            if (idSeed != null) return Objects.equals(idSeed, key.idSeed);
            return Objects.equals(application, key.application) &&
                   Objects.equals(cluster, key.cluster);
        }
    }
    private final Map<Key, LoadBalancerInstance> instances = new HashMap<>();
    private boolean throwOnCreate = false;
    private boolean supportsProvisioning = true;
    private final AtomicBoolean uuid = new AtomicBoolean(true);

    public Map<LoadBalancerId, LoadBalancerInstance> instances() {
        return instances.entrySet().stream().collect(toMap(e -> new LoadBalancerId(e.getKey().application, e.getKey().cluster),
                                                           Map.Entry::getValue));
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
    public Protocol protocol(boolean enclave) {
        return Protocol.ipv4;
    }

    @Override
    public LoadBalancerInstance provision(LoadBalancerSpec spec, Optional<String> idSeed) {
        if (throwOnCreate) throw new IllegalStateException("Did not expect a new load balancer to be created");
        var instance = new LoadBalancerInstance(
                idSeed,
                Optional.of(DomainName.of("lb-" + spec.application().toShortString() + "-" + spec.cluster().value())),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new DnsZone("zone-id-1")),
                Collections.singleton(4443),
                ImmutableSet.of("10.2.3.0/24", "10.4.5.0/24"),
                spec.reals(),
                spec.settings(),
                spec.settings().isPrivateEndpoint() ? List.of(PrivateServiceId.of("service")) : List.of(),
                spec.cloudAccount());
        instances.put(new Key(spec.application(), spec.cluster(), idSeed.orElse(null)), instance);
        return instance;
    }

    @Override
    public LoadBalancerInstance configure(LoadBalancerInstance instance, LoadBalancerSpec spec, boolean force) {
        var id = new Key(spec.application(), spec.cluster(), instance.idSeed().orElse(null));
        var oldInstance = requireNonNull(instances.get(id), "expected existing load balancer " + id);
        if (!force && !oldInstance.reals().isEmpty() && spec.reals().isEmpty()) {
            throw new IllegalArgumentException("Refusing to remove all reals from load balancer " + id);
        }
        var updated = instance.with(spec.reals(),
                                    spec.settings(),
                                    spec.settings().isPrivateEndpoint() ? Optional.of(PrivateServiceId.of("service")) : Optional.empty());
        instances.put(id, updated);
        return updated;
    }

    @Override
    public void reallocate(LoadBalancerInstance provisioned, LoadBalancerSpec spec) {
        instances.put(new Key(spec.application(), spec.cluster(), provisioned.idSeed().get()),
                      requireNonNull(instances.remove(new Key(null, null, provisioned.idSeed().get())))); // ᕙ༼◕_◕༽ᕤ
    }

    @Override
    public void remove(LoadBalancer loadBalancer) {
        requireNonNull(instances.remove(new Key(loadBalancer.id().application(),
                                                loadBalancer.id().cluster(),
                                                loadBalancer.instance().get().idSeed().orElse(null))),
                       "expected load balancer to exist: " + loadBalancer.id());
    }

    @Override
    public Availability healthy(Endpoint endpoint, Optional<String> idSeed) {
        return Availability.ready;
    }

}
