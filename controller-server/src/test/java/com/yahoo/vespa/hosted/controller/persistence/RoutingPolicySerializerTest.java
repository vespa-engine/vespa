// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import org.junit.Test;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicySerializerTest {

    private final RoutingPolicySerializer serializer = new RoutingPolicySerializer();

    @Test
    public void test_serialization() {
        var owner = ApplicationId.defaultId();
        var rotations = Set.of(RotationName.from("r1"), RotationName.from("r2"));
        var policies = ImmutableSet.of(new RoutingPolicy(owner,
                                                         ClusterSpec.Id.from("my-cluster1"),
                                                         ZoneId.from("prod", "us-north-1"),
                                                         HostName.from("long-and-ugly-name"),
                                                         Optional.of("zone1"),
                                                         rotations),
                                       new RoutingPolicy(owner,
                                                         ClusterSpec.Id.from("my-cluster2"),
                                                         ZoneId.from("prod", "us-north-2"),
                                                         HostName.from("long-and-ugly-name-2"),
                                                         Optional.empty(),
                                                         rotations));
        var serialized = serializer.fromSlime(owner, serializer.toSlime(policies));
        assertEquals(policies.size(), serialized.size());
        for (Iterator<RoutingPolicy> it1 = policies.iterator(), it2 = serialized.iterator(); it1.hasNext();) {
            var expected = it1.next();
            var actual = it2.next();
            assertEquals(expected.owner(), actual.owner());
            assertEquals(expected.cluster(), actual.cluster());
            assertEquals(expected.zone(), actual.zone());
            assertEquals(expected.canonicalName(), actual.canonicalName());
            assertEquals(expected.dnsZone(), actual.dnsZone());
            assertEquals(expected.rotations(), actual.rotations());
        }
    }

}
