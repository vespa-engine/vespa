// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.OrderedFlagSource;

import java.util.Map;


/**
 * @author arnej
 */
public class LegacyFlags {

    public static final String GEO_POSITIONS = "v7-geo-positions";
    public static final String FOO_BAR = "foo-bar"; // for testing

    private static FlagSource buildFrom(Map<String, String> legacyOverrides) {
        var flags = new InMemoryFlagSource();
        for (var entry : legacyOverrides.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            boolean legacyWanted = Boolean.valueOf(value);
            switch (key) {
            case GEO_POSITIONS:
                flags = flags.withBooleanFlag(Flags.USE_V8_GEO_POSITIONS.id(), ! legacyWanted);
                break;
            case FOO_BAR:
                // ignored
                break;
            default:
                throw new IllegalArgumentException("Unknown legacy override: "+key);
            }
        }
        return flags;
    }

    public static FlagSource from(ApplicationPackage pkg, FlagSource input) {
        var overrides = buildFrom(pkg.legacyOverrides());
        FlagSource result = new OrderedFlagSource(overrides, input);
        return result;
    }
}
