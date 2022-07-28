// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;
import com.yahoo.vespa.hosted.controller.routing.ZoneRoutingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class ZoneRoutingPolicySerializerTest {

    @Test
    void serialization() {
        var serializer = new ZoneRoutingPolicySerializer(new RoutingPolicySerializer());
        var zone = ZoneId.from("prod", "us-north-1");
        var policy = new ZoneRoutingPolicy(zone,
                RoutingStatus.create(RoutingStatus.Value.out, RoutingStatus.Agent.operator,
                        Instant.ofEpochMilli(123)));
        var serialized = serializer.fromSlime(zone, serializer.toSlime(policy));
        assertEquals(policy, serialized);
    }

}
