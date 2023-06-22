package com.yahoo.vespa.hosted.controller.certificate;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;

/**
 * An unassigned certificate, which exists in a pre-provisioned pool of certificates. Once assigned to an application,
 * the certificate is removed from the pool.
 *
 * @param certificate Details of the certificate
 * @param state Current state of this
 *
 * @author andreer
 */
public record UnassignedCertificate(EndpointCertificateMetadata certificate, UnassignedCertificate.State state) {

    public UnassignedCertificate {
        if (certificate.randomizedId().isEmpty()) {
            throw new IllegalArgumentException("randomizedId must be set for a pooled certificate");
        }
    }

    public String id() {
        return certificate.randomizedId().get();
    }

    public UnassignedCertificate withState(State state) {
        return new UnassignedCertificate(certificate, state);
    }

    public enum State {
        /** The certificate is ready for assignment */
        ready,

        /** The certificate is requested and is being provisioned */
        requested
    }

}
