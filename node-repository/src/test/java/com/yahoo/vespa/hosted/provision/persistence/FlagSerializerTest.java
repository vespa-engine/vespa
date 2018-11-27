// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.provision.flag.Flag;
import com.yahoo.vespa.hosted.provision.flag.FlagId;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class FlagSerializerTest {

    @Test
    public void test_serialization() {
        Flag flag = new Flag(FlagId.exclusiveLoadBalancer, true,
                             ImmutableSet.of("host1", "host2"),
                             Collections.singleton(
                                     ApplicationId.from("tenant1", "application1", "default")
                             ));
        Flag serialized = FlagSerializer.fromJson(FlagSerializer.toJson(flag));
        assertEquals(flag.id(), serialized.id());
        assertEquals(flag.isEnabled(), serialized.isEnabled());
        assertEquals(flag.hostnames(), serialized.hostnames());
        assertEquals(flag.applications(), serialized.applications());
    }

}
