// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import org.junit.Test;

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
        ApplicationId owner = ApplicationId.defaultId();
        Set<RotationName> rotations = Set.of(RotationName.from("r1"), RotationName.from("r2"));
        Set<RoutingPolicy> loadBalancers = ImmutableSet.of(new RoutingPolicy(owner,
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
        Set<RoutingPolicy> serialized = serializer.fromSlime(owner, serializer.toSlime(loadBalancers));
        assertEquals(loadBalancers, serialized);
    }

    @Test
    public void test_legacy_serialization() { // TODO: Remove after 7.43 has been released
        String json = "{\n" +
                      "  \"routingPolicies\": [\n" +
                      "    {\n" +
                      "      \"alias\": \"my-pretty-alias\",\n" +
                      "      \"zone\": \"prod.us-north-1\",\n" +
                      "      \"canonicalName\": \"long-and-ugly-name\",\n" +
                      "      \"dnsZone\": \"zone1\",\n" +
                      "      \"rotations\": [\n" +
                      "        \"r1\",\n" +
                      "        \"r2\"\n" +
                      "      ]\n" +
                      "    }\n" +
                      "  ]\n" +
                      "}";
        ApplicationId owner = ApplicationId.defaultId();
        Set<RoutingPolicy> expected = Set.of(new RoutingPolicy(owner,
                                                               ClusterSpec.Id.from("default"),
                                                               ZoneId.from("prod", "us-north-1"),
                                                               HostName.from("long-and-ugly-name"),
                                                               Optional.of("zone1"),
                                                               Set.of(RotationName.from("r1"), RotationName.from("r2"))));
        assertEquals(expected, serializer.fromSlime(owner, SlimeUtils.jsonToSlime(json)));
    }

}
