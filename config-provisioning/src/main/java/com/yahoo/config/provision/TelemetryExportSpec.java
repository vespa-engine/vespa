// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single telemetry exporter specification, defining how to export metrics and/or logs
 * to a customer-owned OTEL endpoint.
 * All secret fields (authSecretName, authUsernameSecretName, authPasswordSecretName)
 * are vault reference names, not actual credential values.
 *
 * @author onur
 */
public record TelemetryExportSpec(String id,
                                  ExporterType type,
                                  Optional<String> endpoint,
                                  Optional<String> project,
                                  Optional<String> authType,
                                  Optional<String> authVault,
                                  Optional<String> authSecretName,
                                  Optional<String> authHeader,
                                  Optional<String> authUsernameSecretName,
                                  Optional<String> authPasswordSecretName,
                                  Optional<String> metricSet,
                                  List<String> logFileTypes) {

    public enum ExporterType { otlp, otlphttp, googlecloud }

    public TelemetryExportSpec {
        Objects.requireNonNull(id, "id must be non-null");
        Objects.requireNonNull(type, "type must be non-null");
        Objects.requireNonNull(endpoint, "endpoint must be non-null (use Optional.empty())");
        Objects.requireNonNull(project, "project must be non-null (use Optional.empty())");
        Objects.requireNonNull(authType, "authType must be non-null");
        Objects.requireNonNull(authVault, "authVault must be non-null");
        Objects.requireNonNull(authSecretName, "authSecretName must be non-null");
        Objects.requireNonNull(authHeader, "authHeader must be non-null");
        Objects.requireNonNull(authUsernameSecretName, "authUsernameSecretName must be non-null");
        Objects.requireNonNull(authPasswordSecretName, "authPasswordSecretName must be non-null");
        Objects.requireNonNull(metricSet, "metricSet must be non-null (use Optional.empty())");
        Objects.requireNonNull(logFileTypes, "logFileTypes must be non-null");
        logFileTypes = List.copyOf(logFileTypes);
    }

}
