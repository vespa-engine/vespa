// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured configuration for exporting telemetry to a customer-owned OTel endpoint.
 * metrics and logs are independently optional — null means that pipeline is disabled.
 *
 * @author onur
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record CustomerOtelExportParameters(
        @JsonProperty("metrics")        CustomerMetricsExporter metrics,
        @JsonProperty("logs")           CustomerLogsExporter logs,
        @JsonProperty("memoryLimitMiB") Integer memoryLimitMiB) {

    private static final int DEFAULT_MEMORY_LIMIT_MIB = 550;

    @JsonCreator
    public CustomerOtelExportParameters {
        memoryLimitMiB = memoryLimitMiB != null ? memoryLimitMiB : DEFAULT_MEMORY_LIMIT_MIB;
    }

    public boolean hasMetricsExporter() { return metrics != null; }
    public boolean hasLogsExporter()   { return logs != null; }

    /**
     * Customer metric pipeline config.
     * exporterType selects the Alloy exporter component: otlphttp (default) or otlp (gRPC).
     * metricSet defaults to "Vespa9".
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public record CustomerMetricsExporter(
            @JsonProperty("exporterType") CustomerExporterConfig.ExporterType exporterType,
            @JsonProperty("endpoint")     String endpoint,
            @JsonProperty("metricSet")    String metricSet) {

        @JsonCreator
        public CustomerMetricsExporter {
            exporterType = exporterType != null ? exporterType : CustomerExporterConfig.ExporterType.otlphttp;
            metricSet    = metricSet    != null ? metricSet    : "Vespa9";
        }
    }

    /**
     * Customer log pipeline config. Supports otlphttp and googlecloud exporters.
     * logFileNames is a list of log file enum names (e.g. CONTAINER_VESPA_LOGS, VAR_LOG_MESSAGES).
     * defaultLogName is used only for the googlecloud exporter (defaults to "vespa-logs").
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public record CustomerLogsExporter(
            @JsonProperty("exporter")       CustomerExporterConfig exporter,
            @JsonProperty("logFileNames")   List<String> logFileNames,
            @JsonProperty("defaultLogName") String defaultLogName) {

        @JsonCreator
        public CustomerLogsExporter {
            logFileNames   = logFileNames   != null ? List.copyOf(logFileNames) : List.of();
            defaultLogName = defaultLogName != null ? defaultLogName : "vespa-logs";
        }
    }

    /**
     * Exporter connectivity config shared across pipeline types.
     *
     * @author onur
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public record CustomerExporterConfig(
            @JsonProperty("exporterType")       ExporterType exporterType,
            @JsonProperty("endpoint")           String endpoint,
            @JsonProperty("googleCloudProject") String googleCloudProject) {

        public enum ExporterType { otlp, otlphttp, googlecloud }

        @JsonCreator
        public CustomerExporterConfig {}

    }
}