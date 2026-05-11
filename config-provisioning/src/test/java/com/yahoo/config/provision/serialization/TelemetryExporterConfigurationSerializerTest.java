// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.serialization;

import com.yahoo.config.provision.TelemetryExporterConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryExporterConfigurationSerializerTest {

    @Test
    void testRoundTripWithBearerToken() {
        var auth = new TelemetryExporterConfiguration.Auth("bearer", "my-vault", "my-token", null, null, null);
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "exp1", "otlphttp", "https://otel.example.com/v1", null,
                auth, List.of("default", "vespa"), List.of("access", "container"));
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripWithApiKey() {
        var auth = new TelemetryExporterConfiguration.Auth("api_key", "my-vault", "my-key", "X-API-Key", null, null);
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "exp1", "otlphttp", "https://otel.example.com/v1", null,
                auth, List.of(), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripWithBasicAuth() {
        var auth = new TelemetryExporterConfiguration.Auth("basic_auth", "my-vault", null, null, "user-secret", "pass-secret");
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "exp1", "otlphttp", "https://otel.example.com/v1", null,
                auth, List.of(), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripWithoutAuth() {
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "exp1", "otlp", "https://otel.example.com:4317", null,
                null, List.of("default"), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripGoogleCloud() {
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "gcp", "googlecloud", null, "my-gcp-project",
                null, List.of(), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripMultipleExporters() {
        var exporter1 = new TelemetryExporterConfiguration.Exporter(
                "first", "otlphttp", "https://first.example.com/v1", null,
                null, List.of("default"), List.of("access"));
        var exporter2 = new TelemetryExporterConfiguration.Exporter(
                "second", "otlp", "https://second.example.com:4317", null,
                new TelemetryExporterConfiguration.Auth("bearer", "vault2", "token2", null, null, null),
                List.of("vespa"), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter1, exporter2));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testEmptyConfigRoundTrip() {
        var config = TelemetryExporterConfiguration.empty();

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertTrue(deserialized.isEmpty());
    }

    @Test
    void testEmptyConfig() {
        var config = TelemetryExporterConfiguration.empty();
        assertTrue(config.isEmpty());
        assertEquals(List.of(), config.exporters());
    }

    @Test
    void testDeserializeFromJsonString() {
        String json = """
                {
                  "exporters": [
                    {
                      "id": "my-exp",
                      "type": "otlphttp",
                      "endpoint": "https://otel.example.com/v1",
                      "auth": {
                        "type": "bearer",
                        "vault": "my-vault",
                        "secretName": "my-token"
                      },
                      "metricSets": ["default", "vespa"],
                      "logFileTypes": ["access"]
                    }
                  ]
                }
                """;
        var config = TelemetryExporterConfigurationSerializer.fromJson(json.getBytes());
        assertEquals(1, config.exporters().size());
        var exporter = config.exporters().get(0);
        assertEquals("my-exp", exporter.id());
        assertEquals("otlphttp", exporter.type());
        assertEquals("https://otel.example.com/v1", exporter.endpoint().get());
        assertTrue(exporter.project().isEmpty());
        assertTrue(exporter.auth().isPresent());
        assertEquals("bearer", exporter.auth().get().type());
        assertEquals("my-vault", exporter.auth().get().vault());
        assertEquals("my-token", exporter.auth().get().secretName().get());
        assertEquals(List.of("default", "vespa"), exporter.metricSets());
        assertEquals(List.of("access"), exporter.logFileTypes());
    }

    @Test
    void testSerializeToJsonString() {
        var auth = new TelemetryExporterConfiguration.Auth("api_key", "vault1", "key1", "X-Key", null, null);
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "exp1", "otlp", "https://ep.example.com:4317", null,
                auth, List.of("default"), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        byte[] json = TelemetryExporterConfigurationSerializer.toJson(config);
        String jsonStr = new String(json, StandardCharsets.UTF_8);
        assertTrue(jsonStr.contains("\"id\":\"exp1\""));
        assertTrue(jsonStr.contains("\"type\":\"otlp\""));
        assertTrue(jsonStr.contains("\"endpoint\":\"https://ep.example.com:4317\""));
        assertTrue(jsonStr.contains("\"vault\":\"vault1\""));
        assertTrue(jsonStr.contains("\"secretName\":\"key1\""));
        assertTrue(jsonStr.contains("\"header\":\"X-Key\""));
        assertTrue(jsonStr.contains("\"metricSets\":[\"default\"]"));
        assertFalse(jsonStr.contains("\"project\""));
    }

    @Test
    void testRoundTripWithEndpointAndProject() {
        var exporter = new TelemetryExporterConfiguration.Exporter(
                "exp1", "otlphttp", "https://otel.example.com/v1", "my-project",
                null, List.of("default"), List.of("access"));
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripAllAuthTypes() {
        var bearerExporter = new TelemetryExporterConfiguration.Exporter(
                "bearer-exp", "otlphttp", "https://first.example.com/v1", null,
                new TelemetryExporterConfiguration.Auth("bearer", "vault1", "token1", null, null, null),
                List.of("default"), List.of());
        var apiKeyExporter = new TelemetryExporterConfiguration.Exporter(
                "apikey-exp", "otlp", "https://second.example.com:4317", null,
                new TelemetryExporterConfiguration.Auth("api_key", "vault2", "key2", "X-API-Key", null, null),
                List.of(), List.of("access"));
        var basicAuthExporter = new TelemetryExporterConfiguration.Exporter(
                "basic-exp", "otlphttp", "https://third.example.com/v1", null,
                new TelemetryExporterConfiguration.Auth("basic_auth", "vault3", null, null, "user-secret", "pass-secret"),
                List.of("vespa"), List.of("container"));
        var config = new TelemetryExporterConfiguration(List.of(bearerExporter, apiKeyExporter, basicAuthExporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testExporterEquality() {
        var auth = new TelemetryExporterConfiguration.Auth("bearer", "vault", "secret", null, null, null);
        var a = new TelemetryExporterConfiguration.Exporter("id", "otlp", "https://ep", null, auth, List.of("m1"), List.of("l1"));
        var b = new TelemetryExporterConfiguration.Exporter("id", "otlp", "https://ep", null, auth, List.of("m1"), List.of("l1"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testAuthEquality() {
        var a = new TelemetryExporterConfiguration.Auth("bearer", "vault", "secret", null, null, null);
        var b = new TelemetryExporterConfiguration.Auth("bearer", "vault", "secret", null, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testAuthInequality() {
        var a = new TelemetryExporterConfiguration.Auth("bearer", "vault", "secret", null, null, null);
        var b = new TelemetryExporterConfiguration.Auth("bearer", "vault", "other-secret", null, null, null);
        var c = new TelemetryExporterConfiguration.Auth("api_key", "vault", "secret", null, null, null);
        var d = new TelemetryExporterConfiguration.Auth("bearer", "other-vault", "secret", null, null, null);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }

    @Test
    void testExporterInequality() {
        var a = new TelemetryExporterConfiguration.Exporter("id", "otlp", "https://ep", null, null, List.of(), List.of());
        var b = new TelemetryExporterConfiguration.Exporter("other-id", "otlp", "https://ep", null, null, List.of(), List.of());
        var c = new TelemetryExporterConfiguration.Exporter("id", "otlphttp", "https://ep", null, null, List.of(), List.of());
        var d = new TelemetryExporterConfiguration.Exporter("id", "otlp", "https://other", null, null, List.of(), List.of());
        var e = new TelemetryExporterConfiguration.Exporter("id", "otlp", "https://ep", null, null, List.of("m1"), List.of());
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, e);
    }

    @Test
    void testConfigEquality() {
        var exporter = new TelemetryExporterConfiguration.Exporter("id", "otlp", "https://ep", null, null, List.of(), List.of());
        var a = new TelemetryExporterConfiguration(List.of(exporter));
        var b = new TelemetryExporterConfiguration(List.of(exporter));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

}
