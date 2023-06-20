// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;
import java.util.Optional;

/**
 * This class is used for metadata about an application's endpoint certificate on the controller.
 * <p>
 * It has more properties than com.yahoo.config.model.api.EndpointCertificateMetadata.
 *
 * @author andreer
 */
public record EndpointCertificateMetadata(String keyName, String certName, int version, long lastRequested,
                                          String rootRequestId, // The id of the first request made for this certificate. Should not change.
                                          Optional<String> leafRequestId, // The id of the last known request made for this certificate. Changes on refresh, may be outdated!
                                          List<String> requestedDnsSans, String issuer, Optional<Long> expiry,
                                          Optional<Long> lastRefreshed, Optional<String> randomizedId) {

    public EndpointCertificateMetadata withRandomizedId(String randomizedId) {
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
                this.lastRefreshed,
                Optional.of(randomizedId));
    }

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
                this.lastRefreshed,
                this.randomizedId);
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
                this.lastRefreshed,
                this.randomizedId);
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
                this.lastRefreshed,
                this.randomizedId);
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
                this.lastRefreshed,
                this.randomizedId);
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
                Optional.of(lastRefreshed),
                this.randomizedId);
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
                this.lastRefreshed,
                this.randomizedId);
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
                this.lastRefreshed,
                this.randomizedId);
    }

}
