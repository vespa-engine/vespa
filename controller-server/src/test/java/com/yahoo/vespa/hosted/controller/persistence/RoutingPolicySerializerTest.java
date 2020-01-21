// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.Status;
import org.junit.Test;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicySerializerTest {

    private final RoutingPolicySerializer serializer = new RoutingPolicySerializer();

    @Test
    public void serialization() {
        var owner = ApplicationId.defaultId();
        var endpoints = Set.of(EndpointId.of("r1"), EndpointId.of("r2"));
        var id1 = new RoutingPolicyId(owner,
                                      ClusterSpec.Id.from("my-cluster1"),
                                      ZoneId.from("prod", "us-north-1"));
        var id2 = new RoutingPolicyId(owner,
                                      ClusterSpec.Id.from("my-cluster2"),
                                      ZoneId.from("prod", "us-north-2"));
        var policies = ImmutableMap.of(id1, new RoutingPolicy(id1,
                                                         HostName.from("long-and-ugly-name"),
                                                         Optional.of("zone1"),
                                                         endpoints, new Status(true, GlobalRouting.DEFAULT_STATUS)),
                                       id2, new RoutingPolicy(id2,
                                                         HostName.from("long-and-ugly-name-2"),
                                                         Optional.empty(),
                                                         endpoints, new Status(false,
                                                                               new GlobalRouting(GlobalRouting.Status.out,
                                                                                                 GlobalRouting.Agent.tenant,
                                                                                                 Instant.ofEpochSecond(123)))));
        var serialized = serializer.fromSlime(owner, serializer.toSlime(policies));
        assertEquals(policies.size(), serialized.size());
        for (Iterator<RoutingPolicy> it1 = policies.values().iterator(), it2 = serialized.values().iterator(); it1.hasNext();) {
            var expected = it1.next();
            var actual = it2.next();
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.canonicalName(), actual.canonicalName());
            assertEquals(expected.dnsZone(), actual.dnsZone());
            assertEquals(expected.endpoints(), actual.endpoints());
            assertEquals(expected.status(), actual.status());
        }
    }

    // TODO(mpolden): Remove after January 2020
    @Test
    public void legacy_serialization() {
        var json = "{\"routingPolicies\":[{\"cluster\":\"default\",\"zone\":\"prod.us-north-1\",\"canonicalName\":\"lb-host\",\"dnsZone\":\"dnsZoneId\",\"rotations\":[\"default\"],\"active\":true}]}";
        var owner = ApplicationId.defaultId();
        var serialized = serializer.fromSlime(owner, SlimeUtils.jsonToSlime(json));
        var id = new RoutingPolicyId(owner, ClusterSpec.Id.from("default"), ZoneId.from("prod", "us-north-1"));
        var expected = Map.of(id, new RoutingPolicy(id, HostName.from("lb-host"), Optional.of("dnsZoneId"),
                                                    Set.of(EndpointId.defaultId()), new Status(true, GlobalRouting.DEFAULT_STATUS)));
        assertEquals(expected, serialized);
    }

}
