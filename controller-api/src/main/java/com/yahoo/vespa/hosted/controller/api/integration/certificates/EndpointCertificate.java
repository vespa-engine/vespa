// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;
import java.util.Optional;

/**
 * This holds information about an application's endpoint certificate.
 *
 * @author andreer
 */
public record EndpointCertificate(String keyName, String certName, int version, long lastRequested,
                                  String rootRequestId, // The id of the first request made for this certificate. Should not change.
                                  Optional<String> leafRequestId, // The id of the last known request made for this certificate. Changes on refresh, may be outdated!
                                  List<String> requestedDnsSans, String issuer, Optional<Long> expiry,
                                  Optional<Long> lastRefreshed, Optional<String> randomizedId) {

    public EndpointCertificate withRandomizedId(String randomizedId) {
        return new EndpointCertificate(
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

    public EndpointCertificate withKeyName(String keyName) {
        return new EndpointCertificate(
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

    public EndpointCertificate withCertName(String certName) {
        return new EndpointCertificate(
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

    public EndpointCertificate withVersion(int version) {
        return new EndpointCertificate(
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

    public EndpointCertificate withLastRequested(long lastRequested) {
        return new EndpointCertificate(
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

    public EndpointCertificate withLastRefreshed(long lastRefreshed) {
        return new EndpointCertificate(
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

    public EndpointCertificate withRootRequestId(String rootRequestId) {
        return new EndpointCertificate(
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

    public EndpointCertificate withLeafRequestId(Optional<String> leafRequestId) {
        return new EndpointCertificate(
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
