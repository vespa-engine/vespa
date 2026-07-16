// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.telemetry;

import ai.vespa.telemetry.TelemetryConfig;
import ai.vespa.telemetry.api.NoopTelemetry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author onur
 */
class TelemetryProviderTest {

    @Test
    void disabled_provider_hands_out_the_noop() {
        TelemetryConfig config = new TelemetryConfig.Builder()
                .enabled(false)
                .samplingRatio(1.0)
                .endpointHostnameFile("conf/vespa/otel/host-hostname")
                .build();
        assertSame(NoopTelemetry.INSTANCE, new TelemetryProvider(config).get());
    }
}
