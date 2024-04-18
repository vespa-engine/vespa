// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.config.model.ApplicationConfigProducerRoot.StatePortInfo;
import com.yahoo.config.provision.Zone;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author olaa
 */
public class OpenTelemetryConfigGenerator {

    private final boolean useTls;
    private final String ca_file;
    private final String cert_file;
    private final String key_file;
    private List<StatePortInfo> statePorts = new ArrayList<>();

    OpenTelemetryConfigGenerator(Zone zone) {
        boolean isCd = true;
        boolean isPublic = true;
        if (zone != null) {
            isCd = zone.system().isCd();
            isPublic = zone.system().isPublic();
            this.useTls = true;
        } else {
            // for manual testing
            this.useTls = false;
        }
        if (isCd) {
            if (isPublic) {
                this.ca_file = "/opt/vespa/var/vespa/trust-store.pem";
                this.cert_file = "/var/lib/sia/certs/vespa.external.cd.tenant.cert.pem";
                this.key_file = "/var/lib/sia/keys/vespa.external.cd.tenant.key.pem";
            } else {
                this.ca_file = "/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem";
                this.cert_file = "/var/lib/sia/certs/vespa.vespa.cd.tenant.cert.pem";
                this.key_file = "/var/lib/sia/keys/vespa.vespa.cd.tenant.key.pem";
            }
        } else {
            if (isPublic) {
                this.ca_file = "/opt/vespa/var/vespa/trust-store.pem";
                this.cert_file = "/var/lib/sia/certs/vespa.external.tenant.cert.pem";
                this.key_file = "/var/lib/sia/keys/vespa.external.tenant.key.pem";
            } else {
                this.ca_file = "/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem";
                this.cert_file = "/var/lib/sia/certs/vespa.vespa.tenant.cert.pem";
                this.key_file = "/var/lib/sia/keys/vespa.vespa.tenant.key.pem";
            }
        }
    }

    String receiverName(int index) {
        return "prometheus_simple/s" + index;
    }

    private void addReceivers(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("receivers");
        g.writeStartObject();
        int counter = 0;
        for (var statePort : statePorts) {
            addReceiver(g, ++counter, statePort);
        }
        g.writeEndObject(); // receivers
    }
    private void addReceiver(JsonGenerator g, int index, StatePortInfo statePort) throws java.io.IOException {
        g.writeFieldName(receiverName(index));
        g.writeStartObject();
        g.writeStringField("collection_interval", "60s");
        g.writeStringField("endpoint", statePort.hostName() + ":" + statePort.portNumber());
        addUrlInfo(g);
        if (useTls) addTls(g);
        {
            g.writeFieldName("labels");
            g.writeStartObject();
            g.writeStringField("service_type", statePort.serviceType());
            g.writeEndObject();
        }
        g.writeEndObject();
    }
    private void addTls(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("tls");
        g.writeStartObject();
        g.writeStringField("ca_file", ca_file);
        g.writeStringField("cert_file", cert_file);
        g.writeBooleanField("insecure_skip_verify", true);
        g.writeStringField("key_file", key_file);
        g.writeEndObject(); // tls
    }
    private void addUrlInfo(JsonGenerator g) throws java.io.IOException {
        g.writeStringField("metrics_path", "/state/v1/metrics");
        g.writeFieldName("params");
        g.writeStartObject();
        g.writeStringField("format", "prometheus");
        g.writeEndObject();
    }
    private void addExporters(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("exporters");
        g.writeStartObject();
        addFileExporter(g);
        g.writeEndObject(); // exporters
    }
    private void addFileExporter(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("file");
        g.writeStartObject();
        g.writeStringField("path", getDefaults().underVespaHome("logs/vespa/otel-test.json"));
        {
            g.writeFieldName("rotation");
            g.writeStartObject();
            g.writeNumberField("max_megabytes", 10);
            g.writeNumberField("max_days", 3);
            g.writeNumberField("max_backups", 1);
            g.writeEndObject(); // rotation
        }
        g.writeEndObject(); // file
    }
    private void addServiceBlock(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("service");
        g.writeStartObject();
        {
            g.writeFieldName("telemetry");
            g.writeStartObject();
            {
                g.writeFieldName("logs");
                g.writeStartObject();
                g.writeStringField("level", "debug");
                g.writeEndObject();
            }
            g.writeEndObject();
        }
        {
            g.writeFieldName("pipelines");
            g.writeStartObject();
            addMetricsPipelines(g);
            g.writeEndObject(); // pipelines
        }
        g.writeEndObject(); // service
    }
    private void addMetricsPipelines(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("metrics");
        g.writeStartObject();
        {
            g.writeFieldName("receivers");
            g.writeStartArray();
            int counter = 0;
            for (var statePort : statePorts) {
                g.writeString(receiverName(++counter));
            }
            g.writeEndArray();
        }
        g.writeFieldName("processors");
        g.writeStartArray();
        g.writeEndArray();
        {
            g.writeFieldName("exporters");
            g.writeStartArray();
            g.writeString("file");
            g.writeEndArray();
        }
        g.writeEndObject(); // metrics
    }

    // For now - mostly dummy config
    /*
    TODO: Create config
        1. polling /state/v1 handler of every service (mostly done)
        2. Processing with mapping/filtering from metric sets
        3. Exporter to correct endpoint (alternatively amended)
     */
    public String generate() {
        if (statePorts.isEmpty()) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            JsonGenerator g = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);
            g.writeStartObject();
            addReceivers(g);
            addExporters(g);
            addServiceBlock(g);
            g.writeEndObject(); // root
            g.close();
        } catch (java.io.IOException e) {
            System.err.println("unexpected error: " + e);
            return "";
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    void addStatePorts(List<StatePortInfo> portList) {
        this.statePorts = portList;
    }

    List<String> referencedPaths() {
        return List.of(ca_file, cert_file, key_file);
    }
}
