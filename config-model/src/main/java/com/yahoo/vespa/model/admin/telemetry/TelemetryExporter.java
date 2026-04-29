// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.telemetry;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for a single telemetry exporter defined in the admin section of services.xml.
 *
 * @author onur
 */
public class TelemetryExporter {

    public enum ExporterType { otlp, otlphttp, googlecloud }

    private static final String DEFAULT_METRIC_SET = "Vespa9";

    private final String id;
    private final ExporterType type;
    private final Optional<String> endpoint;
    private final Optional<String> project;
    private final Optional<TelemetryAuth> auth;
    private final String metricSet;
    private final List<String> logFileTypes;

    public TelemetryExporter(String id, ExporterType type, String endpoint, String project,
                             Optional<TelemetryAuth> auth, String metricSet,
                             List<String> logFileTypes) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("exporter id must be non-empty");
        if (type == null) throw new IllegalArgumentException("exporter type must be specified");
        if (type != ExporterType.googlecloud && (endpoint == null || endpoint.isBlank()))
            throw new IllegalArgumentException("endpoint is required for exporter type '" + type + "'");
        if (type == ExporterType.googlecloud && (project == null || project.isBlank()))
            throw new IllegalArgumentException("project is required for exporter type 'googlecloud'");
        this.id = id;
        this.type = type;
        this.endpoint = Optional.ofNullable(endpoint);
        this.project = Optional.ofNullable(project);
        this.auth = auth;
        this.metricSet = metricSet != null ? metricSet : DEFAULT_METRIC_SET;
        this.logFileTypes = logFileTypes != null ? List.copyOf(logFileTypes) : List.of();
    }

    public String id() { return id; }
    public ExporterType type() { return type; }
    public Optional<String> endpoint() { return endpoint; }
    public Optional<String> project() { return project; }
    public Optional<TelemetryAuth> auth() { return auth; }
    public String metricSet() { return metricSet; }
    public List<String> logFileTypes() { return logFileTypes; }

}
