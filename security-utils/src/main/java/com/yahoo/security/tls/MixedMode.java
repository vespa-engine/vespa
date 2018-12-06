// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;

/**
 * @author bjorncs
 */
public enum MixedMode {
    PLAINTEXT_CLIENT_MIXED_SERVER("plaintext_client_mixed_server"),
    TLS_CLIENT_MIXED_SERVER("tls_client_mixed_server");

    final String configValue;

    MixedMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    static MixedMode fromConfigValue(String configValue) {
        return Arrays.stream(values())
                .filter(v -> v.configValue.equals(configValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown value: " + configValue));
    }
}
