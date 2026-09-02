// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProfilingSettingsTest {

    @Test
    void serialization() throws IOException {
        verifySerialization(ProfilingSettings.createDisabled());
        verifySerialization(new ProfilingSettings(true, List.of(ProfiledService.CONTAINER)));
        verifySerialization(new ProfilingSettings(true, List.of(ProfiledService.PROTON, ProfiledService.DISTRIBUTOR)));
        verifySerialization(new ProfilingSettings(false, List.of(ProfiledService.METRICS_PROXY)));
    }

    @Test
    void unknown_fields_are_ignored() throws IOException {
        var settings = Jackson.mapper().readValue(
                "{\"enabled\": true, \"services\": [\"CONTAINER\"], \"someFutureField\": 42}",
                ProfilingSettings.class);
        assertEquals(Set.of(ProfiledService.CONTAINER), settings.profiledServices());
    }

    @Test
    void missing_fields_fall_back_to_disabled() throws IOException {
        var settings = Jackson.mapper().readValue("{}", ProfilingSettings.class);
        assertEquals(Set.of(), settings.profiledServices());
    }

    /** Neither switch alone profiles anything: both enabled and a non-empty list are required. */
    @Test
    void profiled_services_requires_both_switches() {
        assertEquals(Set.of(), new ProfilingSettings(true, List.of()).profiledServices());
        assertEquals(Set.of(), new ProfilingSettings(false, List.of(ProfiledService.CONTAINER)).profiledServices());
        assertEquals(Set.of(), ProfilingSettings.createDisabled().profiledServices());
        assertEquals(Set.of(ProfiledService.CONTAINER),
                     new ProfilingSettings(true, List.of(ProfiledService.CONTAINER)).profiledServices());
    }

    private void verifySerialization(ProfilingSettings settings) throws IOException {
        var mapper = Jackson.mapper();
        String json = mapper.writeValueAsString(settings);
        ProfilingSettings deserialized = mapper.readValue(json, ProfilingSettings.class);
        assertEquals(settings, deserialized);
    }

}
