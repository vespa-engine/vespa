package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class UnassignedCertificateSerializerTest {

    @Test
    public void serialization() {
        EndpointCertificateMetadata certificate = new EndpointCertificateMetadata("keyName", "certName", 1, 0,
                                                                                  "rootRequestId", Optional.of("leafRequestId"),
                                                                                  List.of("SAN1", "SAN2"), "issuer", Optional.of(3L),
                                                                                  Optional.of(4L), Optional.of("my-id"));
        UnassignedCertificate unassignedCertificate = new UnassignedCertificate(certificate, UnassignedCertificate.State.ready);
        UnassignedCertificateSerializer serializer = new UnassignedCertificateSerializer();
        assertEquals(unassignedCertificate, serializer.fromSlime(serializer.toSlime(unassignedCertificate)));
    }


}
