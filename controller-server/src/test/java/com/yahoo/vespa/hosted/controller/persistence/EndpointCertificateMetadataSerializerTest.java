package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class EndpointCertificateMetadataSerializerTest {

    private final EndpointCertificateMetadata sampleWithExpiryAndLastRefreshed =
            new EndpointCertificateMetadata("keyName", "certName", 1, 0, "requestId", List.of("SAN1", "SAN2"), "issuer", java.util.Optional.of(1628000000L), Optional.of(1612000000L));

    private final EndpointCertificateMetadata sampleWithoutExpiry =
            new EndpointCertificateMetadata("keyName", "certName", 1, 0, "requestId", List.of("SAN1", "SAN2"), "issuer", Optional.empty(), Optional.empty());

    @Test
    public void serializeWithExpiryAndLastRefreshed() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\",\"expiry\":1628000000,\"lastRefreshed\":1612000000}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithExpiryAndLastRefreshed).toString());
    }

    @Test
    public void serializeWithoutExpiryAndLastRefreshed() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\"}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithoutExpiry).toString());
    }

    @Test
    public void deserializeFromJsonWithExpiryAndLastRefreshed() {
        assertEquals(
                sampleWithExpiryAndLastRefreshed,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\",\"expiry\":1628000000,\"lastRefreshed\":1612000000}"));
    }

    @Test
    public void deserializeFromJsonWithoutExpiryAndLastRefreshed() {
        assertEquals(
                sampleWithoutExpiry,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\"}"));
    }
}
