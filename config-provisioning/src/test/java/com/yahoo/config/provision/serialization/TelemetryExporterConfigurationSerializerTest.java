// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.serialization;

import com.yahoo.config.provision.TelemetryExporterConfiguration;
import com.yahoo.config.provision.TelemetryExporterConfiguration.Auth;
import com.yahoo.config.provision.TelemetryExporterConfiguration.Exporter;
import com.yahoo.config.provision.TelemetryExporterConfiguration.Exporter.ExporterType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryExporterConfigurationSerializerTest {

    @Test
    void testRoundTripWithBearerToken() {
        var auth = Auth.bearerToken("my-vault", "my-token");
        var exporter = new Exporter("exp1", ExporterType.otlphttp, Optional.of("https://otel.example.com/v1"), Optional.empty(),
                Optional.of(auth), List.of("default", "vespa"), List.of("access", "container"));
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripWithApiKey() {
        var auth = Auth.apiKey("my-vault", "my-key", "X-API-Key");
        var exporter = new Exporter("exp1", ExporterType.otlphttp, Optional.of("https://otel.example.com/v1"), Optional.empty(),
                Optional.of(auth), List.of(), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripWithBasicAuth() {
        var auth = Auth.basicAuth("my-vault", "user-secret", "pass-secret");
        var exporter = new Exporter("exp1", ExporterType.otlphttp, Optional.of("https://otel.example.com/v1"), Optional.empty(),
                Optional.of(auth), List.of(), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripWithoutAuth() {
        var exporter = new Exporter("exp1", ExporterType.otlp, Optional.of("https://otel.example.com:4317"), Optional.empty(),
                Optional.empty(), List.of("default"), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripGoogleCloud() {
        var exporter = new Exporter("gcp", ExporterType.googlecloud, Optional.empty(), Optional.of("my-gcp-project"),
                Optional.empty(), List.of(), List.of());
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripMultipleExporters() {
        var exporter1 = new Exporter("first", ExporterType.otlphttp, Optional.of("https://first.example.com/v1"), Optional.empty(),
                Optional.empty(), List.of("default"), List.of("access"));
        var exporter2 = new Exporter("second", ExporterType.otlp, Optional.of("https://second.example.com:4317"), Optional.empty(),
                Optional.of(Auth.bearerToken("vault2", "token2")), List.of("vespa"), List.of());
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
        assertEquals(ExporterType.otlphttp, exporter.type());
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
        var auth = Auth.apiKey("vault1", "key1", "X-Key");
        var exporter = new Exporter("exp1", ExporterType.otlp, Optional.of("https://ep.example.com:4317"), Optional.empty(),
                Optional.of(auth), List.of("default"), List.of());
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
        var exporter = new Exporter("exp1", ExporterType.otlphttp, Optional.of("https://otel.example.com/v1"), Optional.of("my-project"),
                Optional.empty(), List.of("default"), List.of("access"));
        var config = new TelemetryExporterConfiguration(List.of(exporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testRoundTripAllAuthTypes() {
        var bearerExporter = new Exporter("bearer-exp", ExporterType.otlphttp, Optional.of("https://first.example.com/v1"), Optional.empty(),
                Optional.of(Auth.bearerToken("vault1", "token1")), List.of("default"), List.of());
        var apiKeyExporter = new Exporter("apikey-exp", ExporterType.otlp, Optional.of("https://second.example.com:4317"), Optional.empty(),
                Optional.of(Auth.apiKey("vault2", "key2", "X-API-Key")), List.of(), List.of("access"));
        var basicAuthExporter = new Exporter("basic-exp", ExporterType.otlphttp, Optional.of("https://third.example.com/v1"), Optional.empty(),
                Optional.of(Auth.basicAuth("vault3", "user-secret", "pass-secret")), List.of("vespa"), List.of("container"));
        var config = new TelemetryExporterConfiguration(List.of(bearerExporter, apiKeyExporter, basicAuthExporter));

        var deserialized = TelemetryExporterConfigurationSerializer.fromJson(TelemetryExporterConfigurationSerializer.toJson(config));
        assertEquals(config, deserialized);
    }

    @Test
    void testExporterEquality() {
        var auth = Auth.bearerToken("vault", "secret");
        var a = new Exporter("id", ExporterType.otlp, Optional.of("https://ep"), Optional.empty(), Optional.of(auth), List.of("m1"), List.of("l1"));
        var b = new Exporter("id", ExporterType.otlp, Optional.of("https://ep"), Optional.empty(), Optional.of(auth), List.of("m1"), List.of("l1"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testAuthEquality() {
        var a = Auth.bearerToken("vault", "secret");
        var b = Auth.bearerToken("vault", "secret");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testAuthInequality() {
        var a = Auth.bearerToken("vault", "secret1");
        var b = Auth.bearerToken("vault", "secret2");
        var c = Auth.apiKey("vault", "secret1", "header");
        var d = Auth.bearerToken("other-vault", "secret1");
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }

    @Test
    void testExporterInequality() {
        var a = new Exporter("id", ExporterType.otlp, Optional.of("https://ep"), Optional.empty(), Optional.empty(), List.of(), List.of());
        var b = new Exporter("other-id", ExporterType.otlp, Optional.of("https://ep"), Optional.empty(), Optional.empty(), List.of(), List.of());
        var c = new Exporter("id", ExporterType.otlphttp, Optional.of("https://ep"), Optional.empty(), Optional.empty(), List.of(), List.of());
        var d = new Exporter("id", ExporterType.otlp, Optional.of("https://other"), Optional.empty(), Optional.empty(), List.of(), List.of());
        var e = new Exporter("id", ExporterType.otlp, Optional.of("https://ep"), Optional.empty(), Optional.empty(), List.of("m1"), List.of());
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, e);
    }

    @Test
    void testConfigEquality() {
        var exporter = new Exporter("id", ExporterType.otlp, Optional.of("https://ep"), Optional.empty(), Optional.empty(), List.of(), List.of());
        var a = new TelemetryExporterConfiguration(List.of(exporter));
        var b = new TelemetryExporterConfiguration(List.of(exporter));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

}
