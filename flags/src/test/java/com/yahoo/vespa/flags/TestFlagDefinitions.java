// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.List;

/**
 * Test-only {@link FlagDefinitions} implementation registered via
 * {@code META-INF/services/com.yahoo.vespa.flags.FlagDefinitions} on the test classpath.
 *
 * <p>Exercises the SPI loaded from {@link Flags}'s static initializer.</p>
 */
public class TestFlagDefinitions implements FlagDefinitions {

    public static final String SPI_TEST_FLAG_ID = "spi-test-flag";
    public static final String SPI_TEST_STRING_FLAG_ID = "spi-test-permanent-flag";

    @Override
    public void register() {
        Flags.defineFeatureFlag(
                SPI_TEST_FLAG_ID, false,
                List.of("spi-test"), "1970-01-01", "2100-01-01",
                "Flag registered by the test SPI implementation",
                "Takes effect immediately");

        Flags.defineStringFlag(
                SPI_TEST_STRING_FLAG_ID, "default",
                List.of("spi-test"), "1970-01-01", "2100-01-01",
                "String flag registered by the test SPI implementation",
                "Takes effect immediately");
    }
}
