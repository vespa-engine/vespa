// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EndpointCertificateMetadataSerializerTest {

    private final EndpointCertificateMetadata sampleWithOptionalFieldsSet =
            new EndpointCertificateMetadata("keyName", "certName", 1, 0, "rootRequestId", Optional.of("leafRequestId"), List.of("SAN1", "SAN2"), "issuer", java.util.Optional.of(1628000000L), Optional.of(1612000000L));

    private final EndpointCertificateMetadata sampleWithoutOptionalFieldsSet =
            new EndpointCertificateMetadata("keyName", "certName", 1, 0, "rootRequestId", Optional.empty(), List.of("SAN1", "SAN2"), "issuer", Optional.empty(), Optional.empty());

    @Test
    void serialize_with_optional_fields() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"rootRequestId\",\"leafRequestId\":\"leafRequestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\",\"expiry\":1628000000,\"lastRefreshed\":1612000000}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithOptionalFieldsSet).toString());
    }

    @Test
    void serialize_without_optional_fields() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"rootRequestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\"}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithoutOptionalFieldsSet).toString());
    }

    @Test
    void deserialize_from_json_with_optional_fields() {
        assertEquals(
                sampleWithOptionalFieldsSet,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"rootRequestId\",\"leafRequestId\":\"leafRequestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\",\"expiry\":1628000000,\"lastRefreshed\":1612000000}"));
    }

    @Test
    void deserialize_from_json_without_optional_fields() {
        assertEquals(
                sampleWithoutOptionalFieldsSet,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"rootRequestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\"}"));
    }
}
