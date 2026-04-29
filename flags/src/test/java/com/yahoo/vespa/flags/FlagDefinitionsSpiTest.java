// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Flags} discovers and invokes {@link FlagDefinitions} implementations
 * via {@link ServiceLoader} during its static initializer, and that the resulting flags are
 * visible through the public registry.
 *
 * @see TestFlagDefinitions
 */
class FlagDefinitionsSpiTest {

    @Test
    void serviceLoader_finds_test_spi_implementation() {
        long count = ServiceLoader.load(FlagDefinitions.class).stream()
                .filter(provider -> provider.type().equals(TestFlagDefinitions.class))
                .count();
        assertEquals(1, count, "TestFlagDefinitions should be discoverable via ServiceLoader");
    }

    @Test
    void spi_registered_feature_flag_is_visible_in_flags_registry() {
        Optional<FlagDefinition> definition = Flags.getFlag(new FlagId(TestFlagDefinitions.SPI_TEST_FLAG_ID));
        assertTrue(definition.isPresent(),
                   "Flag registered by FlagDefinitions SPI implementation should be present in Flags");
    }

    @Test
    void spi_registered_string_flag_is_visible_in_flags_registry() {
        Optional<FlagDefinition> definition = Flags.getFlag(new FlagId(TestFlagDefinitions.SPI_TEST_STRING_FLAG_ID));
        assertTrue(definition.isPresent(),
                   "String flag registered by FlagDefinitions SPI implementation should be present in Flags");
    }

    @Test
    void permanentFlags_static_init_does_not_double_register() {
        // PermanentFlags field initializers delegate to Flags.defineXxxFlag(...), which forces
        // Flags.<clinit> to complete (including SPI loading) before PermanentFlags is fully
        // initialized. The AtomicBoolean guard inside Flags makes any subsequent invocation
        // of loadFlagDefinitionsSpi() a no-op, so SPI flags are not re-registered.
        String firstFlagId = PermanentFlags.SHARED_HOST.id().toString();
        assertEquals("shared-host", firstFlagId);

        // SPI flags remain present after PermanentFlags has been initialized.
        assertTrue(Flags.getFlag(new FlagId(TestFlagDefinitions.SPI_TEST_FLAG_ID)).isPresent());
    }
}
