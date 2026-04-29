// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.telemetry;

import java.util.Optional;

/**
 * Authentication configuration for a telemetry exporter.
 * Supports bearer-token, api-key, and basic-auth types.
 *
 * @author onur
 */
public class TelemetryAuth {

    public enum Type { bearer, api_key, basic_auth }

    private final Type type;
    private final String vault;
    private final Optional<String> name;
    private final Optional<String> header;
    private final Optional<String> username;
    private final Optional<String> password;

    private TelemetryAuth(Type type, String vault, Optional<String> name,
                          Optional<String> header, Optional<String> username,
                          Optional<String> password) {
        if (type == null) throw new IllegalArgumentException("auth type must be specified");
        if (vault == null || vault.isBlank()) throw new IllegalArgumentException("vault must be non-empty");
        this.type = type;
        this.vault = vault;
        this.name = name;
        this.header = header;
        this.username = username;
        this.password = password;
    }

    public static TelemetryAuth bearerToken(String vault, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("bearer-token name must be non-empty");
        return new TelemetryAuth(Type.bearer, vault, Optional.of(name), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static TelemetryAuth apiKey(String vault, String name, String header) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("api-key name must be non-empty");
        if (header == null || header.isBlank()) throw new IllegalArgumentException("api-key header must be non-empty");
        return new TelemetryAuth(Type.api_key, vault, Optional.of(name), Optional.of(header), Optional.empty(), Optional.empty());
    }

    public static TelemetryAuth basicAuth(String vault, String username, String password) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("basic-auth username must be non-empty");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("basic-auth password must be non-empty");
        return new TelemetryAuth(Type.basic_auth, vault, Optional.empty(), Optional.empty(), Optional.of(username), Optional.of(password));
    }

    public Type type() { return type; }
    public String vault() { return vault; }
    public Optional<String> name() { return name; }
    public Optional<String> header() { return header; }
    public Optional<String> username() { return username; }
    public Optional<String> password() { return password; }

}
