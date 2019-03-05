// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.vespa.hosted.provision.lb.DnsZone;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class LoadBalancerSerializerTest {

    @Test
    public void test_serialization() {
        LoadBalancer loadBalancer = new LoadBalancer(new LoadBalancerId(ApplicationId.from("tenant1",
                                                                                           "application1",
                                                                                           "default"),
                                                                        ClusterSpec.Id.from("qrs")),
                                                     new LoadBalancerInstance(
                                                             HostName.from("lb-host"),
                                                             Optional.of(new DnsZone("zone-id-1")),
                                                             ImmutableSet.of(4080, 4443),
                                                             ImmutableSet.of("10.2.3.4/24"),
                                                             ImmutableSet.of(new Real(HostName.from("real-1"),
                                                                                      "127.0.0.1",
                                                                                      4080),
                                                                             new Real(HostName.from("real-2"),
                                                                                      "127.0.0.2",
                                                                                      4080))),
                                                     ImmutableSet.of(RotationName.from("eu-cluster"),
                                                                     RotationName.from("us-cluster")),
                                                     false);

        LoadBalancer serialized = LoadBalancerSerializer.fromJson(LoadBalancerSerializer.toJson(loadBalancer));
        assertEquals(loadBalancer.id(), serialized.id());
        assertEquals(loadBalancer.instance().hostname(), serialized.instance().hostname());
        assertEquals(loadBalancer.instance().dnsZone(), serialized.instance().dnsZone());
        assertEquals(loadBalancer.instance().ports(), serialized.instance().ports());
        assertEquals(loadBalancer.instance().networks(), serialized.instance().networks());
        assertEquals(loadBalancer.rotations(), serialized.rotations());
        assertEquals(loadBalancer.inactive(), serialized.inactive());
        assertEquals(loadBalancer.instance().reals(), serialized.instance().reals());
    }

}
