// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.flags.custom.NodeMaintainerDurations;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class NodeMaintainerDurationsTest {
    @Test
    public void testSerialization() {
        String flagValueJson = "{ \"reboot_interval\": 3, \"fail_grace\": 7}";
        var rawFlag = JsonNodeRawFlag.fromJson(flagValueJson);
        var serializer = new JacksonSerializer<>(NodeMaintainerDurations.class);
        var durations = serializer.deserialize(rawFlag);
        assertEquals(Optional.of(Duration.ofSeconds(3)), durations.getDuration("reboot_interval"));
        assertEquals(Optional.of(Duration.ofSeconds(7)), durations.getDuration("fail_grace"));
        assertEquals(Optional.empty(), durations.getDuration("non-existing"));

        RawFlag serializedRawFlag = serializer.serialize(durations);
        JsonTestHelper.assertJsonEquals(flagValueJson, serializedRawFlag.asJson());
    }

    @Test
    public void testFlag() {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        var durations = new NodeMaintainerDurations(Map.of("reboot_interval", 3L, "fail_grace", 7L));
        flagSource.withJacksonFlag(Flags.NODE_MAINTAINER_DURATIONS.id(), durations, NodeMaintainerDurations.class);
        NodeMaintainerDurations resolvedDurations = Flags.NODE_MAINTAINER_DURATIONS.bindTo(flagSource).value();
        assertEquals(Optional.of(Duration.ofSeconds(3)), resolvedDurations.getDuration("reboot_interval"));
        assertEquals(Optional.of(Duration.ofSeconds(7)), resolvedDurations.getDuration("fail_grace"));
        assertEquals(Optional.empty(), resolvedDurations.getDuration("non-existing"));
    }
}
