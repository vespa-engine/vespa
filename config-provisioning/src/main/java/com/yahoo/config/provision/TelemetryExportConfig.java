// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable telemetry export configuration for an application deployment.
 * Persisted to session ZK during deploy and propagated to the Application ZK object via DeploymentConfigStore.
 *
 * @author onur
 */
public class TelemetryExportConfig {

    private static final TelemetryExportConfig EMPTY = new TelemetryExportConfig(List.of());

    private final List<Exporter> exporters;

    public TelemetryExportConfig(List<Exporter> exporters) {
        this.exporters = List.copyOf(Objects.requireNonNull(exporters));
    }

    public List<Exporter> exporters() { return exporters; }

    public boolean isEmpty() { return exporters.isEmpty(); }

    public static TelemetryExportConfig empty() { return EMPTY; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return exporters.equals(((TelemetryExportConfig) o).exporters);
    }

    @Override
    public int hashCode() { return exporters.hashCode(); }

    @Override
    public String toString() { return "TelemetryExportConfig{exporters=" + exporters + "}"; }

    /** A single telemetry exporter targeting an external endpoint or cloud project. */
    public static class Exporter {

        private final String id;
        private final String type;
        private final Optional<String> endpoint;
        private final Optional<String> project;
        private final Optional<Auth> auth;
        private final List<String> metricSets;
        private final List<String> logFileTypes;

        public Exporter(String id, String type, String endpoint, String project,
                        Auth auth, List<String> metricSets, List<String> logFileTypes) {
            this.id = Objects.requireNonNull(id);
            this.type = Objects.requireNonNull(type);
            this.endpoint = Optional.ofNullable(endpoint);
            this.project = Optional.ofNullable(project);
            this.auth = Optional.ofNullable(auth);
            this.metricSets = metricSets != null ? List.copyOf(metricSets) : List.of();
            this.logFileTypes = logFileTypes != null ? List.copyOf(logFileTypes) : List.of();
        }

        public String id() { return id; }
        public String type() { return type; }
        public Optional<String> endpoint() { return endpoint; }
        public Optional<String> project() { return project; }
        public Optional<Auth> auth() { return auth; }
        public List<String> metricSets() { return metricSets; }
        public List<String> logFileTypes() { return logFileTypes; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Exporter that = (Exporter) o;
            return id.equals(that.id) && type.equals(that.type) && endpoint.equals(that.endpoint)
                    && project.equals(that.project) && auth.equals(that.auth)
                    && metricSets.equals(that.metricSets) && logFileTypes.equals(that.logFileTypes);
        }

        @Override
        public int hashCode() { return Objects.hash(id, type, endpoint, project, auth, metricSets, logFileTypes); }

        @Override
        public String toString() { return "Exporter{id=" + id + ", type=" + type + "}"; }
    }

    /** Authentication credentials for an exporter. All secret fields are vault references, not actual values. */
    public static class Auth {

        private final String type;
        private final String vault;
        private final Optional<String> secretName;
        private final Optional<String> header;
        private final Optional<String> usernameSecretName;
        private final Optional<String> passwordSecretName;

        public Auth(String type, String vault, String secretName, String header,
                    String usernameSecretName, String passwordSecretName) {
            this.type = Objects.requireNonNull(type);
            this.vault = Objects.requireNonNull(vault);
            this.secretName = Optional.ofNullable(secretName);
            this.header = Optional.ofNullable(header);
            this.usernameSecretName = Optional.ofNullable(usernameSecretName);
            this.passwordSecretName = Optional.ofNullable(passwordSecretName);
        }

        public String type() { return type; }
        public String vault() { return vault; }
        public Optional<String> secretName() { return secretName; }
        public Optional<String> header() { return header; }
        public Optional<String> usernameSecretName() { return usernameSecretName; }
        public Optional<String> passwordSecretName() { return passwordSecretName; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Auth that = (Auth) o;
            return type.equals(that.type) && vault.equals(that.vault) && secretName.equals(that.secretName)
                    && header.equals(that.header) && usernameSecretName.equals(that.usernameSecretName)
                    && passwordSecretName.equals(that.passwordSecretName);
        }

        @Override
        public int hashCode() { return Objects.hash(type, vault, secretName, header, usernameSecretName, passwordSecretName); }

        @Override
        public String toString() { return "Auth{type=" + type + ", vault=" + vault + "}"; }
    }

}
