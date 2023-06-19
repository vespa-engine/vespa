// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicySerializerTest {

    private final RoutingPolicySerializer serializer = new RoutingPolicySerializer();

    @Test
    void serialization() {
        var owner = ApplicationId.defaultId();
        var instanceEndpoints = Set.of(EndpointId.of("r1"), EndpointId.of("r2"));
        var applicationEndpoints = Set.of(EndpointId.of("a1"));
        var id1 = new RoutingPolicyId(owner,
                                      ClusterSpec.Id.from("my-cluster1"),
                                      ZoneId.from("prod", "us-north-1"));
        var id2 = new RoutingPolicyId(owner,
                                      ClusterSpec.Id.from("my-cluster2"),
                                      ZoneId.from("prod", "us-north-2"));
        var policies = List.of(new RoutingPolicy(id1,
                                                 Optional.of(HostName.of("long-and-ugly-name")),
                                                 Optional.empty(),
                                                 Optional.of("zone1"),
                                                 Set.of(),
                                                 Set.of(),
                                                 RoutingStatus.DEFAULT,
                                                 false),
                               new RoutingPolicy(id2,
                                                 Optional.of(HostName.of("long-and-ugly-name-2")),
                                                 Optional.empty(),
                                                 Optional.empty(),
                                                 instanceEndpoints,
                                                 Set.of(),
                                                 new RoutingStatus(RoutingStatus.Value.out,
                                                                   RoutingStatus.Agent.tenant,
                                                                   Instant.ofEpochSecond(123)),
                                                 true),
                               new RoutingPolicy(id1,
                                                 Optional.empty(),
                                                 Optional.of("127.0.0.1"),
                                                 Optional.of("zone2"),
                                                 instanceEndpoints,
                                                 applicationEndpoints,
                                                 RoutingStatus.DEFAULT,
                                                 true));
        var serialized = serializer.fromSlime(owner, serializer.toSlime(policies));
        assertEquals(policies.size(), serialized.size());
        for (Iterator<RoutingPolicy> it1 = policies.iterator(), it2 = serialized.iterator(); it1.hasNext(); ) {
            var expected = it1.next();
            var actual = it2.next();
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.canonicalName(), actual.canonicalName());
            assertEquals(expected.ipAddress(), actual.ipAddress());
            assertEquals(expected.dnsZone(), actual.dnsZone());
            assertEquals(expected.instanceEndpoints(), actual.instanceEndpoints());
            assertEquals(expected.applicationEndpoints(), actual.applicationEndpoints());
            assertEquals(expected.routingStatus(), actual.routingStatus());
            assertEquals(expected.isPublic(), actual.isPublic());
        }
    }

}
