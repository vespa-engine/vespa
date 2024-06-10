// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.metric.model.prometheus.PrometheusUtil;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.config.model.ApplicationConfigProducerRoot.StatePortInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
    private final Zone zone;
    private final ApplicationId applicationId;
    private final boolean isHostedVespa;

    OpenTelemetryConfigGenerator(Zone zone, ApplicationId applicationId, boolean isHostedVespa) {
        this.zone = zone;
        this.applicationId = applicationId;
        this.isHostedVespa = isHostedVespa;
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
            var dimVals = serviceAttributes(statePort.service());
            // these will be tagged as dimension-name/label-value
            // attributes on all metrics from this /state/v1 port
            g.writeStartObject();
            for (var entry : dimVals.entrySet()) {
                if (entry.getValue() != null) {
                    g.writeStringField(entry.getKey(), entry.getValue());
                }
            }
            String ph = findParentHost(statePort.hostName());
            if (isHostedVespa && ph != null) {
                g.writeStringField("parentHostname", ph);
            }
            g.writeEndObject();
        }
        g.writeEndObject();
    }
    // note: this pattern should match entire node name
    static private final Pattern expectedNodeName1 = Pattern.compile("[a-z0-9]+-v6-[0-9]+[.].+");
    static private final Pattern expectedNodeName2 = Pattern.compile("[a-z]*[0-9]+[a-z][.].+");
    // matches the part we want to replace with just a dot
    static private final Pattern replaceNodeName1 = Pattern.compile("-v6-[0-9]+[.]");
    static private final Pattern replaceNodeName2 = Pattern.compile("[a-z][.]");
    static String findParentHost(String nodeName) {
        if (expectedNodeName1.matcher(nodeName).matches()) {
            return replaceNodeName1.matcher(nodeName).replaceFirst(".");
        }
        if (expectedNodeName2.matcher(nodeName).matches()) {
            return replaceNodeName2.matcher(nodeName).replaceFirst(".");
        }
        return null;
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
    private void addProcessors(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("processors");
        g.writeStartObject();
        addResourceProcessor(g);
        addRenameProcessor(g);
        g.writeEndObject();
    }
    private void addRenameProcessor(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("metricstransform/rename");
        g.writeStartObject();
        g.writeFieldName("transforms");
        g.writeStartArray();
        var metrics = MetricsConsumer.vespa9.metrics();
        for (var metric : metrics.values()) {
            if (! metric.name.equals(metric.outputName)) {
                String oldName = PrometheusUtil.sanitize(metric.name);
                String newName = PrometheusUtil.sanitize(metric.outputName);
                addRenameAction(g, oldName, newName);
            }
        }
        g.writeEndArray();
        g.writeEndObject();
    }
    private void addRenameAction(JsonGenerator g, String oldName, String newName) throws java.io.IOException {
        g.writeStartObject();
        g.writeStringField("include", oldName);
        g.writeStringField("action", "update");
        g.writeStringField("new_name", newName);
        g.writeEndObject();
    }
    private void addResourceProcessor(JsonGenerator g) throws java.io.IOException {
        g.writeFieldName("resource");
        g.writeStartObject();
        g.writeFieldName("attributes");
        g.writeStartArray();
        // common attributes for all metrics from all services;
        // which application and which cloud/system/zone/environment
        addAttributeInsert(g, PublicDimensions.ZONE, zoneAttr());
        addAttributeInsert(g, PublicDimensions.APPLICATION_ID, appIdAttr());
        addAttributeInsert(g, "system", systemAttr());
        addAttributeInsert(g, "tenantName", tenantAttr());
        addAttributeInsert(g, "applicationName", appNameAttr());
        addAttributeInsert(g, "instanceName", appInstanceAttr());
        addAttributeInsert(g, "cloud", cloudAttr());
        g.writeEndArray();
        g.writeEndObject();
    }
    private void addAttributeInsert(JsonGenerator g, String key, String value) throws java.io.IOException {
        if (value == null) return;
        g.writeStartObject();
        g.writeStringField("key", key);
        g.writeStringField("value", value);
        g.writeStringField("action", "insert");
        g.writeEndObject();
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
        g.writeString("metricstransform/rename");
        g.writeString("resource");
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
            addProcessors(g);
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

    private String zoneAttr() {
        if (zone == null) return null;
        return zone.environment().value() + "." + zone.region().value();
    }
    private String appIdAttr() {
        if (applicationId == null) return null;
        return applicationId.toFullString();
    }
    private String systemAttr() {
        if (zone == null) return null;
        return zone.system().value();
    }
    private String tenantAttr() {
        if (applicationId == null) return null;
        return applicationId.tenant().value();
    }
    private String appNameAttr() {
        if (applicationId == null) return null;
        return applicationId.application().value();
    }
    private String appInstanceAttr() {
        if (applicationId == null) return null;
        return applicationId.instance().value();
    }
    private String cloudAttr() {
        if (zone == null) return null;
        return zone.cloud().name().value();
    }

    private String getDeploymentCluster(ClusterSpec cluster) {
        if (applicationId == null) return null;
        if (zone == null) return null;
        String appString = applicationId.toFullString();
        return String.join(".", appString,
                           zone.environment().value(),
                           zone.region().value(),
                           cluster.id().value());
    }

    private Map<String, String> serviceAttributes(Service svc) {
        Map<String, String> dimvals = new LinkedHashMap<>();
        dimvals.put("instance", svc.getServiceName()); // should maybe be "local_service_name" ?
        dimvals.put("instanceType", svc.getServiceType()); // maybe "local_service_type", or remove
        String cName = svc.getServicePropertyString("clustername", null);
        if (cName != null) {
            // overridden by cluster membership below (if available)
            dimvals.put(PublicDimensions.INTERNAL_CLUSTER_ID, cName);
        }
        String cType = svc.getServicePropertyString("clustertype", null);
        if (cType != null) {
            // overridden by cluster membership below (if available)
            dimvals.put(PublicDimensions.INTERNAL_CLUSTER_TYPE, cType);
        }
        var hostResource = svc.getHost();
        if (hostResource != null) {
            hostResource.spec().membership().map(ClusterMembership::cluster).ifPresent(cluster -> {
                    dimvals.put(PublicDimensions.DEPLOYMENT_CLUSTER, getDeploymentCluster(cluster));
                    // overrides value above
                    dimvals.put(PublicDimensions.INTERNAL_CLUSTER_TYPE, cluster.type().name());
                    // alternative to above
                    dimvals.put(PublicDimensions.INTERNAL_CLUSTER_ID, cluster.id().value());
                    cluster.group().ifPresent(group -> dimvals.put(PublicDimensions.GROUP_ID, group.toString()));
                });
        }
        return dimvals;
    }
}
