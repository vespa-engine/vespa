package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class EndpointCertificateMetadataSerializerTest {

    private final EndpointCertificateMetadata sampleWithExpiry =
            new EndpointCertificateMetadata("keyName", "certName", 1, 0, "requestId", List.of("SAN1", "SAN2"), "issuer", java.util.Optional.of(1628000000L));

    private final EndpointCertificateMetadata sampleWithoutExpiry =
            new EndpointCertificateMetadata("keyName", "certName", 1, 0, "requestId", List.of("SAN1", "SAN2"), "issuer", Optional.empty());

    @Test
    public void serializeWithExpiry() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\",\"expiry\":1628000000}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithExpiry).toString());
    }

    @Test
    public void serializeWithoutExpiry() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\"}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithoutExpiry).toString());
    }

    @Test
    public void deserializeFromJsonWithExpiry() {
        EndpointCertificateMetadata sampleWithExpiry = this.sampleWithExpiry;
        EndpointCertificateMetadata actual = EndpointCertificateMetadataSerializer.fromJsonString(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\",\"expiry\":1628000000}");

        System.out.println(sampleWithExpiry.equals(actual));

        assertEquals(
                sampleWithExpiry,
                actual);
    }

    @Test
    public void deserializeFromJsonWithoutExpiry() {
        assertEquals(
                sampleWithoutExpiry,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"lastRequested\":0,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"],\"issuer\":\"issuer\"}"));
    }
}
