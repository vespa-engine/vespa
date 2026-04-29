// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.telemetry;

import java.util.Optional;

/**
 * Authentication configuration for a telemetry exporter.
 * Supports bearer-token, api-key, and basic-auth types.
 * All secret fields are vault reference names, not actual credential values.
 *
 * @author onur
 */
public class TelemetryAuth {

    public enum Type { bearer, api_key, basic_auth }

    private final Type type;
    private final String vault;
    private final Optional<String> secretName;
    private final Optional<String> header;
    private final Optional<String> usernameSecretName;
    private final Optional<String> passwordSecretName;

    private TelemetryAuth(Type type, String vault, Optional<String> secretName,
                          Optional<String> header, Optional<String> usernameSecretName,
                          Optional<String> passwordSecretName) {
        if (type == null) throw new IllegalArgumentException("auth type must be specified");
        if (vault == null || vault.isBlank()) throw new IllegalArgumentException("vault must be non-empty");
        this.type = type;
        this.vault = vault;
        this.secretName = secretName;
        this.header = header;
        this.usernameSecretName = usernameSecretName;
        this.passwordSecretName = passwordSecretName;
    }

    public static TelemetryAuth bearerToken(String vault, String secretName) {
        if (secretName == null || secretName.isBlank()) throw new IllegalArgumentException("bearer-token secret-name must be non-empty");
        return new TelemetryAuth(Type.bearer, vault, Optional.of(secretName), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static TelemetryAuth apiKey(String vault, String secretName, String header) {
        if (secretName == null || secretName.isBlank()) throw new IllegalArgumentException("api-key secret-name must be non-empty");
        if (header == null || header.isBlank()) throw new IllegalArgumentException("api-key header must be non-empty");
        return new TelemetryAuth(Type.api_key, vault, Optional.of(secretName), Optional.of(header), Optional.empty(), Optional.empty());
    }

    public static TelemetryAuth basicAuth(String vault, String usernameSecretName, String passwordSecretName) {
        if (usernameSecretName == null || usernameSecretName.isBlank()) throw new IllegalArgumentException("basic-auth username-secret must be non-empty");
        if (passwordSecretName == null || passwordSecretName.isBlank()) throw new IllegalArgumentException("basic-auth password-secret must be non-empty");
        return new TelemetryAuth(Type.basic_auth, vault, Optional.empty(), Optional.empty(), Optional.of(usernameSecretName), Optional.of(passwordSecretName));
    }

    public Type type() { return type; }
    public String vault() { return vault; }
    public Optional<String> secretName() { return secretName; }
    public Optional<String> header() { return header; }
    public Optional<String> usernameSecretName() { return usernameSecretName; }
    public Optional<String> passwordSecretName() { return passwordSecretName; }

}
