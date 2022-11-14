// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import ai.vespa.http.DomainName;
import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.lb.DnsZone;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class LoadBalancerSerializerTest {

    private static final LoadBalancerId loadBalancerId = new LoadBalancerId(
            ApplicationId.from("tenant1", "application1", "default"), ClusterSpec.Id.from("qrs"));;

    @Test
    public void test_serialization() {
        var now = Instant.now();
        var loadBalancer = new LoadBalancer(loadBalancerId,
                                            Optional.of(new LoadBalancerInstance(
                                                    Optional.of(DomainName.of("lb-host")),
                                                    Optional.empty(),
                                                    Optional.of(new DnsZone("zone-id-1")),
                                                    ImmutableSet.of(4080, 4443),
                                                    ImmutableSet.of("10.2.3.4/24"),
                                                    ImmutableSet.of(new Real(DomainName.of("real-1"),
                                                                             "127.0.0.1",
                                                                             4080),
                                                                    new Real(DomainName.of("real-2"),
                                                                             "127.0.0.2",
                                                                             4080)),
                                                    CloudAccount.from("012345678912"))),
                                            LoadBalancer.State.active,
                                            now);

        var serialized = LoadBalancerSerializer.fromJson(LoadBalancerSerializer.toJson(loadBalancer));
        assertEquals(loadBalancer.id(), serialized.id());
        assertEquals(loadBalancer.instance().get().hostname(), serialized.instance().get().hostname());
        assertEquals(loadBalancer.instance().get().dnsZone(), serialized.instance().get().dnsZone());
        assertEquals(loadBalancer.instance().get().ports(), serialized.instance().get().ports());
        assertEquals(loadBalancer.instance().get().networks(), serialized.instance().get().networks());
        assertEquals(loadBalancer.state(), serialized.state());
        assertEquals(loadBalancer.changedAt().truncatedTo(MILLIS), serialized.changedAt());
        assertEquals(loadBalancer.instance().get().reals(), serialized.instance().get().reals());
        assertEquals(loadBalancer.instance().get().cloudAccount(), serialized.instance().get().cloudAccount());
    }

    @Test
    public void no_instance_serialization() {
        var now = Instant.now();
        var loadBalancer = new LoadBalancer(loadBalancerId, Optional.empty(), LoadBalancer.State.reserved, now);

        var serialized = LoadBalancerSerializer.fromJson(LoadBalancerSerializer.toJson(loadBalancer));
        assertEquals(loadBalancer.id(), serialized.id());
        assertEquals(loadBalancer.instance(), serialized.instance());
        assertEquals(loadBalancer.state(), serialized.state());
        assertEquals(loadBalancer.changedAt().truncatedTo(MILLIS), serialized.changedAt());
    }

}
