// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.ZoneRoutingPolicy;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ZoneRoutingPolicySerializerTest {

    @Test
    public void serialization() {
        var serializer = new ZoneRoutingPolicySerializer(new RoutingPolicySerializer());
        var zone = ZoneId.from("prod", "us-north-1");
        var policy = new ZoneRoutingPolicy(zone,
                                               GlobalRouting.status(GlobalRouting.Status.out, GlobalRouting.Agent.operator,
                                                                    Instant.ofEpochMilli(123)));
        var serialized = serializer.fromSlime(zone, serializer.toSlime(policy));
        assertEquals(policy, serialized);
    }

}
