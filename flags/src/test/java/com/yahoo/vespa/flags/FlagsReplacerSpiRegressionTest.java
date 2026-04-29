// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for {@link Flags.Replacer}: flags registered via the
 * {@link FlagDefinitions} SPI must be saved on open and restored on close, exactly like
 * flags defined directly in {@link Flags} or {@link PermanentFlags}.
 */
class FlagsReplacerSpiRegressionTest {

    @Test
    void replacer_saves_and_restores_spi_registered_flags() {
        FlagId spiFlag = new FlagId(TestFlagDefinitions.SPI_TEST_FLAG_ID);

        // Sanity: SPI flag is in the registry before any Replacer is opened.
        assertTrue(Flags.getFlag(spiFlag).isPresent(),
                   "SPI flag should be registered by the time test bodies run");

        try (Flags.Replacer replacer = Flags.clearFlagsForTesting()) {
            assertFalse(Flags.getFlag(spiFlag).isPresent(),
                        "Replacer should clear SPI flags from the active map");
        }

        // After close: the originally-saved map is restored, including SPI flags.
        assertTrue(Flags.getFlag(spiFlag).isPresent(),
                   "SPI flag should be restored when the Replacer is closed");
    }

    @Test
    void replacer_can_keep_spi_registered_flags() {
        FlagId spiFlag = new FlagId(TestFlagDefinitions.SPI_TEST_FLAG_ID);

        try (Flags.Replacer replacer = Flags.clearFlagsForTesting(spiFlag)) {
            assertTrue(Flags.getFlag(spiFlag).isPresent(),
                       "Replacer keep-list should retain SPI flags as well");
        }

        assertTrue(Flags.getFlag(spiFlag).isPresent());
    }
}
