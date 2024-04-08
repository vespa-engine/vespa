// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

/**
 * @author olaa
 */
public class OpenTelemetryConfigGenerator {

    // For now - just create dummy config
    /*
    TODO: Create config
        1. polling /state/v1 handler of every service
        2. Processing with mapping/filtering from metric sets
        3. Exporter to correct endpoint (alternatively amended)
     */
    public static String generate() {

        return """
                receivers:
                  prometheus_simple:
                    collection_interval: 60s
                    endpoint: 'localhost:4080'
                    metrics_path: '/state/v1/metrics
                    params:
                      format: 'prometheus'
                    tls:
                      ca_file: '/opt/vespa/var/vespa//trust-store.pem'
                      cert_file: '/var/lib/sia/certs/vespa.external.cd.tenant.cert.pem'
                      insecure_skip_verify: true
                      key_file: '/var/lib/sia/keys/vespa.external.cd.tenant.key.pem'
                exporters:
                  file:
                    path: /opt/vespa/logs/vespa/otel-test.json
                    rotation:
                      max_megabytes: 10
                      max_days: 3
                      max_backups: 1
                service:
                  pipelines:
                    metrics:
                      receivers: [ prometheus_simple ]
                      processors: [ ]
                      exporters: [ file ]
                """;
    }
}
