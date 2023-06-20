package com.yahoo.vespa.hosted.controller.api.integration.certificates;

public record PooledCertificate(EndpointCertificateMetadata metadata, PooledCertificate.State state) {
    public enum State {
        ready,
        requested
    }
}
