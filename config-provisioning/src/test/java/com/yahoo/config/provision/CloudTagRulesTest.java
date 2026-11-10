// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
class CloudTagRulesTest {

    private static final List<CloudName> CLOUDS = List.of(CloudName.AWS, CloudName.GCP, CloudName.AZURE);

    @Test
    void platform_tag_is_valid_on_all_clouds() {
        var tags = Map.of("vai_os_version", "9-6-20261002");
        assertDoesNotThrow(() -> CloudTagRules.validateStructure(tags));
        for (CloudName cloud : CLOUDS)
            tags.forEach((key, value) -> assertDoesNotThrow(() -> CloudTagRules.forCloud(cloud).validate(key, value),
                                                            cloud.value()));
    }

    @Test
    void reserved_names_are_matched_ignoring_case() {
        assertTrue(CloudTagRules.isReservedName("Name"));
        assertTrue(CloudTagRules.isReservedName("APPLICATIONID"));
        assertFalse(CloudTagRules.isReservedName("team"));
    }

    @Test
    void reserved_prefixes_are_matched_ignoring_case() {
        assertEquals(Optional.of(CloudTagRules.PLATFORM_KEY_PREFIX), CloudTagRules.reservedPrefixOf("VAI_os_version"));
        assertEquals(Optional.of("corp:"), CloudTagRules.reservedPrefixOf("Corp:team"));
        assertEquals(Optional.empty(), CloudTagRules.reservedPrefixOf("vai-team"));
    }

    @Test
    void character_rules_apply_to_value_characters_and_length_limits_to_full_value() {
        var gcp = CloudTagRules.forCloud(CloudName.GCP);
        assertThrows(IllegalArgumentException.class, () -> gcp.validate("key", "{X}-a"));
        assertDoesNotThrow(() -> gcp.validate("key", "{X}-a", "-a"));

        var e = assertThrows(IllegalArgumentException.class, () -> gcp.validate("key", "v".repeat(64), ""));
        assertTrue(e.getMessage().contains("GCP limit of 63"), e.getMessage());
    }

}
