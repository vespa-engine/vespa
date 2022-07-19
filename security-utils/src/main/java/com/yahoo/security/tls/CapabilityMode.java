// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;

/**
 * @author bjorncs
 */
public enum CapabilityMode {
    DISABLE("disable"), LOG_ONLY("log_only"), ENFORCE("enforce");

    private final String configValue;

    CapabilityMode(String configValue) { this.configValue = configValue; }

    public String configValue() { return configValue; }

    /** @return Default value when mode is not explicitly specified */
    public static CapabilityMode defaultValue() { return DISABLE; }

    public static CapabilityMode fromConfigValue(String configValue) {
        return Arrays.stream(values())
                .filter(c -> c.configValue.equals(configValue))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown value: " + configValue));
    }
}
