package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class EndpointCertificateMetadataSerializerTest {

    private EndpointCertificateMetadata sample =
            new EndpointCertificateMetadata("keyName", "certName", 1);
    private EndpointCertificateMetadata sampleWithRequestMetadata =
            new EndpointCertificateMetadata("keyName", "certName", 1, "requestId", List.of("SAN1", "SAN2"));

    @Test
    public void serialize() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1}",
                EndpointCertificateMetadataSerializer.toSlime(sample).toString());
    }

    @Test
    public void serializeWithRequestMetadata() {
        assertEquals(
                "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"]}",
                EndpointCertificateMetadataSerializer.toSlime(sampleWithRequestMetadata).toString());
    }

    @Test
    public void deserializeFromJson() {
        assertEquals(
                sample,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1}"));
    }

    @Test
    public void deserializeFromJsonWithRequestMetadata() {
        assertEquals(
                sampleWithRequestMetadata,
                EndpointCertificateMetadataSerializer.fromJsonString(
                        "{\"keyName\":\"keyName\",\"certName\":\"certName\",\"version\":1,\"requestId\":\"requestId\",\"requestedDnsSans\":[\"SAN1\",\"SAN2\"]}"));
    }
}