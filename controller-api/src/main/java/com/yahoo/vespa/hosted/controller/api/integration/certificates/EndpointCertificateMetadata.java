// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;
import java.util.Optional;

/**
 * This record is used for metadata about an application's endpoint certificate on the controller.
 * <p>
 * It has more properties than com.yahoo.config.model.api.EndpointCertificateMetadata.
 *
 * @param rootRequestId The request id of the first request made for this certificate. Should not change.
 * @param leafRequestId The request id of the last known request made for this certificate. Changes on refresh, may be outdated!
 * @author andreer
 */
public record EndpointCertificateMetadata(String keyName, String certName, int version, long lastRequested,
                                          String rootRequestId, Optional<String> leafRequestId,
                                          List<String> requestedDnsSans, String issuer, Optional<Long> expiry,
                                          Optional<Long> lastRefreshed) {

    public EndpointCertificateMetadata withKeyName(String keyName) {
        return new EndpointCertificateMetadata(
                keyName,
                this.certName,
                this.version,
                this.lastRequested,
                this.rootRequestId,
                this.leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                this.lastRefreshed);
    }

    public EndpointCertificateMetadata withCertName(String certName) {
        return new EndpointCertificateMetadata(
                this.keyName,
                certName,
                this.version,
                this.lastRequested,
                this.rootRequestId,
                this.leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                this.lastRefreshed);
    }

    public EndpointCertificateMetadata withVersion(int version) {
        return new EndpointCertificateMetadata(
                this.keyName,
                this.certName,
                version,
                this.lastRequested,
                this.rootRequestId,
                this.leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                this.lastRefreshed);
    }

    public EndpointCertificateMetadata withLastRequested(long lastRequested) {
        return new EndpointCertificateMetadata(
                this.keyName,
                this.certName,
                this.version,
                lastRequested,
                this.rootRequestId,
                this.leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                this.lastRefreshed);
    }

    public EndpointCertificateMetadata withLastRefreshed(long lastRefreshed) {
        return new EndpointCertificateMetadata(
                this.keyName,
                this.certName,
                this.version,
                this.lastRequested,
                this.rootRequestId,
                this.leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                Optional.of(lastRefreshed));
    }

    public EndpointCertificateMetadata withRootRequestId(String rootRequestId) {
        return new EndpointCertificateMetadata(
                this.keyName,
                this.certName,
                this.version,
                this.lastRequested,
                rootRequestId,
                this.leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                lastRefreshed);
    }

    public EndpointCertificateMetadata withLeafRequestId(Optional<String> leafRequestId) {
        return new EndpointCertificateMetadata(
                this.keyName,
                this.certName,
                this.version,
                this.lastRequested,
                this.rootRequestId,
                leafRequestId,
                this.requestedDnsSans,
                this.issuer,
                this.expiry,
                lastRefreshed);
    }
}
