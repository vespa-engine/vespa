// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.Real;
import org.junit.Test;

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
                                                     HostName.from("lb-host"),
                                                     ImmutableSet.of(4080, 4443),
                                                     ImmutableSet.of("10.2.3.4/24"),
                                                     ImmutableSet.of(new Real(HostName.from("real-1"),
                                                                              "127.0.0.1",
                                                                              4080),
                                                                     new Real(HostName.from("real-2"),
                                                                              "127.0.0.2",
                                                                              4080)),
                                                     false);

        LoadBalancer serialized = LoadBalancerSerializer.fromJson(LoadBalancerSerializer.toJson(loadBalancer));
        assertEquals(loadBalancer.id(), serialized.id());
        assertEquals(loadBalancer.hostname(), serialized.hostname());
        assertEquals(loadBalancer.ports(), serialized.ports());
        assertEquals(loadBalancer.networks(), serialized.networks());
        assertEquals(loadBalancer.inactive(), serialized.inactive());
        assertEquals(loadBalancer.reals(), serialized.reals());
    }

}
