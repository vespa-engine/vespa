// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.telemetry;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryExportTest {

    private static final String hosts = "<hosts>"
            + "  <host name=\"myhost0\"><alias>node0</alias></host>"
            + "  <host name=\"myhost1\"><alias>node1</alias></host>"
            + "</hosts>";

    @Test
    void testBearerTokenAuth() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='my-exporter' type='otlphttp' endpoint='https://otel.example.com/v1'>"
                + "        <auth>"
                + "          <bearer-token vault='my-vault' name='my-token'/>"
                + "        </auth>"
                + "        <metric-set name='Vespa9'/>"
                + "        <logs>"
                + "          <file type='container_logs'/>"
                + "          <file type='access_logs'/>"
                + "        </logs>"
                + "      </exporter>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var telemetry = model.getAdmin().getTelemetryExport();
        assertTrue(telemetry.isPresent());

        var exporters = telemetry.get().exporters();
        assertEquals(1, exporters.size());

        var exporter = exporters.get(0);
        assertEquals("my-exporter", exporter.id());
        assertEquals(TelemetryExporter.ExporterType.otlphttp, exporter.type());
        assertEquals("https://otel.example.com/v1", exporter.endpoint().get());

        assertTrue(exporter.auth().isPresent());
        var auth = exporter.auth().get();
        assertEquals(TelemetryAuth.Type.bearer, auth.type());
        assertEquals("my-vault", auth.vault());
        assertEquals("my-token", auth.name().get());
        assertTrue(auth.header().isEmpty());
        assertTrue(auth.username().isEmpty());
        assertTrue(auth.password().isEmpty());

        assertEquals("Vespa9", exporter.metricSet().get());
        assertEquals(List.of("container_logs", "access_logs"), exporter.logFileTypes());
    }

    @Test
    void testApiKeyAuth() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='my-exporter' type='otlphttp' endpoint='https://otel.example.com/v1'>"
                + "        <auth>"
                + "          <api-key vault='my-vault' name='my-key' header='X-API-Key'/>"
                + "        </auth>"
                + "      </exporter>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var auth = model.getAdmin().getTelemetryExport().get().exporters().get(0).auth().get();
        assertEquals(TelemetryAuth.Type.api_key, auth.type());
        assertEquals("my-vault", auth.vault());
        assertEquals("my-key", auth.name().get());
        assertEquals("X-API-Key", auth.header().get());
    }

    @Test
    void testBasicAuth() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='my-exporter' type='otlphttp' endpoint='https://otel.example.com/v1'>"
                + "        <auth>"
                + "          <basic-auth vault='my-vault' username='my-user' password='my-pass'/>"
                + "        </auth>"
                + "      </exporter>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var auth = model.getAdmin().getTelemetryExport().get().exporters().get(0).auth().get();
        assertEquals(TelemetryAuth.Type.basic_auth, auth.type());
        assertEquals("my-vault", auth.vault());
        assertTrue(auth.name().isEmpty());
        assertEquals("my-user", auth.username().get());
        assertEquals("my-pass", auth.password().get());
    }

    @Test
    void testMultipleExporters() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='first' type='otlphttp' endpoint='https://first.example.com/v1'/>"
                + "      <exporter id='second' type='otlp' endpoint='https://second.example.com:4317'/>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var exporters = model.getAdmin().getTelemetryExport().get().exporters();
        assertEquals(2, exporters.size());
        assertEquals("first", exporters.get(0).id());
        assertEquals(TelemetryExporter.ExporterType.otlphttp, exporters.get(0).type());
        assertEquals("second", exporters.get(1).id());
        assertEquals(TelemetryExporter.ExporterType.otlp, exporters.get(1).type());
    }

    @Test
    void testExporterWithoutAuth() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='no-auth' type='otlphttp' endpoint='https://otel.example.com/v1'>"
                + "        <metric-set name='Vespa9'/>"
                + "      </exporter>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var exporter = model.getAdmin().getTelemetryExport().get().exporters().get(0);
        assertTrue(exporter.auth().isEmpty());
        assertEquals("Vespa9", exporter.metricSet().get());
    }

    @Test
    void testExporterDefaultMetricSet() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='minimal' type='otlphttp' endpoint='https://otel.example.com/v1'/>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var exporter = model.getAdmin().getTelemetryExport().get().exporters().get(0);
        assertTrue(exporter.metricSet().isEmpty());
        assertTrue(exporter.logFileTypes().isEmpty());
    }

    @Test
    void testGooglecloudExporterType() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='gcp' type='googlecloud' project='my-gcp-project'/>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        var exporter = model.getAdmin().getTelemetryExport().get().exporters().get(0);
        assertEquals(TelemetryExporter.ExporterType.googlecloud, exporter.type());
        assertEquals("my-gcp-project", exporter.project().get());
        assertTrue(exporter.endpoint().isEmpty());
    }

    @Test
    void testGooglecloudWithoutProjectFails() {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='gcp' type='googlecloud'/>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        var exception = assertThrows(IllegalArgumentException.class, () -> createModel(hosts, services));
        assertTrue(exception.getMessage().contains("project is required for exporter type 'googlecloud'"));
    }

    @Test
    void testOtlpWithoutEndpointFails() {
        String services = "<services>"
                + "  <admin version='4.0'>"
                + "    <telemetry>"
                + "      <exporter id='test' type='otlphttp'/>"
                + "    </telemetry>"
                + "  </admin>"
                + "</services>";

        var exception = assertThrows(IllegalArgumentException.class, () -> createModel(hosts, services));
        assertTrue(exception.getMessage().contains("endpoint is required for exporter type 'otlphttp'"));
    }

    @Test
    void testNoTelemetryElement() throws Exception {
        String services = "<services>"
                + "  <admin version='4.0'/>"
                + "</services>";

        VespaModel model = createModel(hosts, services);
        assertTrue(model.getAdmin().getTelemetryExport().isEmpty());
    }

    @Test
    void testModelClassValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryExporter(null, TelemetryExporter.ExporterType.otlphttp, "https://ep", null, java.util.Optional.empty(), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryExporter("id", null, "https://ep", null, java.util.Optional.empty(), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryExporter("id", TelemetryExporter.ExporterType.otlphttp, null, null, java.util.Optional.empty(), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryExporter("id", TelemetryExporter.ExporterType.googlecloud, null, null, java.util.Optional.empty(), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryExport(List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryAuth.bearerToken("", "name"));
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryAuth.bearerToken("vault", ""));
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryAuth.apiKey("vault", "name", ""));
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryAuth.basicAuth("vault", "", "pass"));
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryAuth.basicAuth("vault", "user", ""));
    }

    private VespaModel createModel(String hosts, String services) throws Exception {
        return createModel(hosts, services, TestDeployState.createBuilder());
    }

    private VespaModel createModel(String hosts, String services, DeployState.Builder deployStateBuilder) throws Exception {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .build();
        return new VespaModel(new NullConfigModelRegistry(), deployStateBuilder
                .applicationPackage(app)
                .modelHostProvisioner(new InMemoryProvisioner(Hosts.readFrom(app.getHosts()), true, false))
                .build());
    }

}
