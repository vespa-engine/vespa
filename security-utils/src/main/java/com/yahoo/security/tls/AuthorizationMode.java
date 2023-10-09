// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;

/**
 * @author bjorncs
 */
public enum AuthorizationMode {
    DISABLE("disable"),
    LOG_ONLY("log_only"),
    ENFORCE("enforce");

    final String configValue;

    AuthorizationMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    /**
     * @return Default value when authorization mode is not explicitly specified
     */
    public static AuthorizationMode defaultValue() {
        return ENFORCE;
    }


    public static AuthorizationMode fromConfigValue(String configValue) {
        return Arrays.stream(values())
                .filter(v -> v.configValue.equals(configValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown value: " + configValue));
    }
}
