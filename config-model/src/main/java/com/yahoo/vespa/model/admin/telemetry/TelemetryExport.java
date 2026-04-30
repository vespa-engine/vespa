// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.telemetry;

import java.util.List;

/**
 * Top-level telemetry export configuration parsed from the admin section of services.xml.
 * Contains one or more telemetry exporters.
 *
 * @author onur
 */
public class TelemetryExport {

    private final List<TelemetryExporter> exporters;

    public TelemetryExport(List<TelemetryExporter> exporters) {
        if (exporters == null || exporters.isEmpty()) throw new IllegalArgumentException("at least one exporter must be defined");
        this.exporters = List.copyOf(exporters);
    }

    public List<TelemetryExporter> exporters() { return exporters; }

}
