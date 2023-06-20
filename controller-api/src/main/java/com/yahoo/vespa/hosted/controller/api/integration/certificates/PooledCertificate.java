package com.yahoo.vespa.hosted.controller.api.integration.certificates;

/**
 * An unassigned certificate, which exists in a pre-provisioned pool of certificates. Once assigned to an application,
 * the pooled certificate is removed from the pool.
 *
 * @param certificate Details of the certificate
 * @param state Current state of this
 *
 * @author andreer
 */
public record PooledCertificate(EndpointCertificateMetadata certificate, PooledCertificate.State state) {

    public PooledCertificate {
        if (certificate.randomizedId().isEmpty()) {
            throw new IllegalArgumentException("randomizedId must be set for a pooled certificate");
        }
    }

    public String id() {
        return certificate.randomizedId().get();
    }

    public PooledCertificate withState(State state) {
        return new PooledCertificate(certificate, state);
    }

    public enum State {
        /** The certificate is ready for assignment */
        ready,

        /** The certificate is requested and is being provisioned */
        requested
    }

}
