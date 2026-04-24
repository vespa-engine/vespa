// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.telemetry;

/**
 * Bearer token authentication for a telemetry exporter, referencing a secret in a vault.
 *
 * @author onur
 */
public record TelemetryAuth(String vault, String name) {

    public TelemetryAuth {
        if (vault == null || vault.isBlank()) throw new IllegalArgumentException("vault must be non-empty");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must be non-empty");
    }

}
