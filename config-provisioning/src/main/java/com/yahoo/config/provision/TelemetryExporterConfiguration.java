// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable telemetry export configuration for an application deployment.
 *
 * @author onur
 */
public record TelemetryExporterConfiguration(List<Exporter> exporters, List<VaultReference> vaultReferences) {

    private static final TelemetryExporterConfiguration EMPTY = new TelemetryExporterConfiguration(List.of(), List.of());

    public TelemetryExporterConfiguration(List<Exporter> exporters) {
        this(exporters, List.of());
    }

    public TelemetryExporterConfiguration {
        exporters = List.copyOf(Objects.requireNonNull(exporters));
        vaultReferences = List.copyOf(Objects.requireNonNull(vaultReferences));
    }

    public boolean isEmpty() { return exporters.isEmpty(); }

    public static TelemetryExporterConfiguration empty() { return EMPTY; }

    /** Returns a new config with resolved tenant vault metadata for vaults referenced by exporters with auth. Throws if any referenced vault is missing. */
    public TelemetryExporterConfiguration withTenantVaultReferences(List<VaultReference> availableVaults) {
        Map<String, VaultReference> vaultsByName = availableVaults.stream()
                .collect(Collectors.toMap(VaultReference::name, v -> v));

        List<VaultReference> resolved = exporters.stream()
                .flatMap(exporter -> exporter.auth().stream())
                .map(auth -> {
                    var ref = vaultsByName.get(auth.vault());
                    if (ref == null)
                        throw new IllegalArgumentException("Telemetry exporter references vault '" + auth.vault() +
                                "' but no matching vault metadata was found");
                    return ref;
                })
                .distinct()
                .toList();

        return new TelemetryExporterConfiguration(exporters, resolved);
    }

    /** A single telemetry exporter targeting an external endpoint or cloud project. */
    public record Exporter(String id, ExporterType type, Optional<String> endpoint, Optional<String> project,
                           Optional<Auth> auth, List<String> metricSets, List<String> logFileTypes) {

        public enum ExporterType { otlp, otlphttp, googlecloud }

        public Exporter {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("exporter id must be non-blank");
            Objects.requireNonNull(type, "exporter type must be specified");
            if (type != ExporterType.googlecloud && (endpoint == null || endpoint.isEmpty() || endpoint.get().isBlank()))
                throw new IllegalArgumentException("endpoint is required for exporter type '" + type + "'");
            if (type == ExporterType.googlecloud && (project == null || project.isEmpty() || project.get().isBlank()))
                throw new IllegalArgumentException("project is required for exporter type 'googlecloud'");
            endpoint = endpoint != null ? endpoint : Optional.empty();
            project = project != null ? project : Optional.empty();
            auth = auth != null ? auth : Optional.empty();
            metricSets = metricSets != null ? List.copyOf(metricSets) : List.of();
            logFileTypes = logFileTypes != null ? List.copyOf(logFileTypes) : List.of();
        }

    }

    /** Authentication credentials for an exporter. All secret fields are vault references, not actual values. */
    public record Auth(String type, String vault, Optional<String> secretName, Optional<String> header,
                       Optional<String> usernameSecretName, Optional<String> passwordSecretName) {

        public Auth {
            if (type == null || type.isBlank()) throw new IllegalArgumentException("auth type must be non-blank");
            if (vault == null || vault.isBlank()) throw new IllegalArgumentException("auth vault must be non-blank");
            secretName = secretName != null ? secretName : Optional.empty();
            header = header != null ? header : Optional.empty();
            usernameSecretName = usernameSecretName != null ? usernameSecretName : Optional.empty();
            passwordSecretName = passwordSecretName != null ? passwordSecretName : Optional.empty();
        }

        public static Auth bearerToken(String vault, String secretName) {
            if (secretName == null || secretName.isBlank()) throw new IllegalArgumentException("bearer-token secret-name must be non-empty");
            return new Auth("bearer", vault, Optional.of(secretName), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static Auth apiKey(String vault, String secretName, String header) {
            if (secretName == null || secretName.isBlank()) throw new IllegalArgumentException("api-key secret-name must be non-empty");
            if (header == null || header.isBlank()) throw new IllegalArgumentException("api-key header must be non-empty");
            return new Auth("api_key", vault, Optional.of(secretName), Optional.of(header), Optional.empty(), Optional.empty());
        }

        public static Auth basicAuth(String vault, String usernameSecretName, String passwordSecretName) {
            if (usernameSecretName == null || usernameSecretName.isBlank()) throw new IllegalArgumentException("basic-auth username-secret-name must be non-empty");
            if (passwordSecretName == null || passwordSecretName.isBlank()) throw new IllegalArgumentException("basic-auth password-secret-name must be non-empty");
            return new Auth("basic_auth", vault, Optional.empty(), Optional.empty(), Optional.of(usernameSecretName), Optional.of(passwordSecretName));
        }
    }

    /** Resolved vault metadata needed by host-admin to read tenant secrets from ASM. */
    public record VaultReference(String id, String name, String externalId) {

        public VaultReference {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("vault id must be non-blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("vault name must be non-blank");
            if (externalId == null || externalId.isBlank()) throw new IllegalArgumentException("vault externalId must be non-blank");
        }

    }

}
