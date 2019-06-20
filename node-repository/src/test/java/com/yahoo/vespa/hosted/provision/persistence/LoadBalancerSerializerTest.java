// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.lb.DnsZone;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author mpolden
 */
public class LoadBalancerSerializerTest {

    @Test
    public void test_serialization() {
        var now = Instant.now();
        var loadBalancer = new LoadBalancer(new LoadBalancerId(ApplicationId.from("tenant1",
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
                                            LoadBalancer.State.active,
                                            now);

        var serialized = LoadBalancerSerializer.fromJson(LoadBalancerSerializer.toJson(loadBalancer), now);
        assertEquals(loadBalancer.id(), serialized.id());
        assertEquals(loadBalancer.instance().hostname(), serialized.instance().hostname());
        assertEquals(loadBalancer.instance().dnsZone(), serialized.instance().dnsZone());
        assertEquals(loadBalancer.instance().ports(), serialized.instance().ports());
        assertEquals(loadBalancer.instance().networks(), serialized.instance().networks());
        assertEquals(loadBalancer.state(), serialized.state());
        assertEquals(loadBalancer.changedAt().truncatedTo(MILLIS), serialized.changedAt());
        assertEquals(loadBalancer.instance().reals(), serialized.instance().reals());
    }

    @Test
    public void test_serialization_legacy() { // TODO(mpolden): Remove after June 2019
        var now = Instant.now();

        var deserialized = LoadBalancerSerializer.fromJson(legacyJson(true).getBytes(StandardCharsets.UTF_8), now);
        assertSame(LoadBalancer.State.inactive, deserialized.state());
        assertEquals(now, deserialized.changedAt());

        deserialized = LoadBalancerSerializer.fromJson(legacyJson(false).getBytes(StandardCharsets.UTF_8), now);
        assertSame(LoadBalancer.State.active, deserialized.state());
    }

    private static String legacyJson(boolean inactive) {
        return "{\n" +
               "  \"id\": \"tenant1:application1:default:qrs\",\n" +
               "  \"hostname\": \"lb-host\",\n" +
               "  \"dnsZone\": \"zone-id-1\",\n" +
               "  \"ports\": [\n" +
               "    4080,\n" +
               "    4443\n" +
               "  ],\n" +
               "  \"networks\": [],\n" +
               "  \"reals\": [],\n" +
               "  \"inactive\": " + inactive + "\n" +
               "}\n";
    }

}
