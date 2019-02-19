// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicySerializerTest {

    @Test
    public void test_serialization() {
        RoutingPolicySerializer serializer = new RoutingPolicySerializer();
        ApplicationId owner = ApplicationId.defaultId();
        Set<RotationName> rotations = Set.of(RotationName.from("r1"), RotationName.from("r2"));
        Set<RoutingPolicy> loadBalancers = ImmutableSet.of(new RoutingPolicy(owner,
                                                                             ZoneId.from("prod", "us-north-1"),
                                                                             HostName.from("my-pretty-alias"),
                                                                             HostName.from("long-and-ugly-name"),
                                                                             Optional.of("zone1"),
                                                                             rotations),
                                                           new RoutingPolicy(owner,
                                                                             ZoneId.from("prod", "us-north-2"),
                                                                             HostName.from("my-pretty-alias-2"),
                                                                             HostName.from("long-and-ugly-name-2"),
                                                                             Optional.empty(),
                                                                             rotations));
        Set<RoutingPolicy> serialized = serializer.fromSlime(owner, serializer.toSlime(loadBalancers));
        assertEquals(loadBalancers, serialized);
    }

    // TODO: Remove after 7.10 has been released
    @Test
    public void test_serialization_old_format() {
        String json = "{\n" +
                      "  \"aliases\": [\n" +
                      "    {\n" +
                      "      \"id\": \"record-id-1\",\n" +
                      "      \"alias\": \"my-pretty-alias\",\n" +
                      "      \"canonicalName\": \"long-and-ugly-name\"" +
                      "    },\n" +
                      "    {\n" +
                      "      \"id\": \"record-id-2\",\n" +
                      "      \"alias\": \"my-pretty-alias-2\",\n" +
                      "      \"canonicalName\": \"long-and-ugly-name-2\"" +
                      "    }\n" +
                      "  ]\n" +
                      "}\n";
        ApplicationId owner = ApplicationId.defaultId();
        Set<RoutingPolicy> loadBalancers = ImmutableSet.of(new RoutingPolicy(owner,
                                                                             ZoneId.from(Environment.defaultEnvironment(), RegionName.defaultName()),
                                                                             HostName.from("my-pretty-alias"),
                                                                             HostName.from("long-and-ugly-name"),
                                                                             Optional.empty(),
                                                                             Collections.emptySet()),
                                                           new RoutingPolicy(owner,
                                                                             ZoneId.from(Environment.defaultEnvironment(), RegionName.defaultName()),
                                                                             HostName.from("my-pretty-alias-2"),
                                                                             HostName.from("long-and-ugly-name-2"),
                                                                             Optional.empty(),
                                                                             Collections.emptySet()));
        RoutingPolicySerializer serializer = new RoutingPolicySerializer();
        assertEquals(loadBalancers, serializer.fromSlime(owner, SlimeUtils.jsonToSlime(json)));
    }

}
