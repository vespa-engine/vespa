// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Definition for permanent feature flags
 *
 * @author bjorncs
 */
public class PermanentFlags {

    static final List<String> OWNERS = List.of();
    static final Instant CREATED_AT = Instant.EPOCH;
    static final Instant EXPIRES_AT = Instant.MAX;

    private PermanentFlags() {}

    private static UnboundBooleanFlag defineFeatureFlag(
            String flagId, boolean defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineFeatureFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundStringFlag defineStringFlag(
            String flagId, String defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineStringFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundIntFlag defineIntFlag(
            String flagId, int defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineIntFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundLongFlag defineLongFlag(
            String flagId, long defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineLongFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundDoubleFlag defineDoubleFlag(
            String flagId, double defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineDoubleFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundJacksonFlag<T> defineJacksonFlag(
            String flagId, T defaultValue, Class<T> jacksonClass,  String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineJacksonFlag(flagId, defaultValue, jacksonClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static String toString(Instant instant) { return DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC).format(instant); }
}
