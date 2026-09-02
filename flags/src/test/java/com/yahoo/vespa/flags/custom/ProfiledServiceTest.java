// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProfiledServiceTest {

    /**
     * The service types must match what config-sentinel stamps as VESPA_SERVICE_NAME, which is
     * config-model's service type: ContainerServiceType for the containers, the lowercased class
     * name for proton ('searchnode') and the distributor. A wrong string silently profiles nothing.
     */
    @Test
    void service_types_match_vespa_service_names() {
        assertEquals(Optional.of(ProfiledService.CONTAINER), ProfiledService.ofServiceType("container"));
        assertEquals(Optional.of(ProfiledService.PROTON), ProfiledService.ofServiceType("searchnode"));
        assertEquals(Optional.of(ProfiledService.DISTRIBUTOR), ProfiledService.ofServiceType("distributor"));
        assertEquals(Optional.of(ProfiledService.METRICS_PROXY), ProfiledService.ofServiceType("metricsproxy-container"));
    }

    @Test
    void unknown_service_types_are_not_profiled() {
        assertEquals(Optional.empty(), ProfiledService.ofServiceType("config-sentinel"));
        assertEquals(Optional.empty(), ProfiledService.ofServiceType("logd"));
        assertEquals(Optional.empty(), ProfiledService.ofServiceType(""));
    }

}
