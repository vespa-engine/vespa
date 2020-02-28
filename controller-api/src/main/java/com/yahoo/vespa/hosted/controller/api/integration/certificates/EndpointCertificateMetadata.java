package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This class is used for metadata about an application's endpoint certificate on the controller.
 * <p>
 * It has more properties than com.yahoo.config.model.api.EndpointCertificateMetadata.
 *
 * @author andreer
 */
public class EndpointCertificateMetadata {

    private final String keyName;
    private final String certName;
    private final int version;
    // TODO: make these fields required once all certs have them stored
    private final Optional<String> request_id;
    private final Optional<List<String>> requestedDnsSans;
    private final Optional<String> issuer;

    public EndpointCertificateMetadata(String keyName, String certName, int version) {
        this(keyName, certName, version, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public EndpointCertificateMetadata(String keyName, String certName, int version, Optional<String> request_id, Optional<List<String>> requestedDnsSans, Optional<String> issuer) {
        this.keyName = keyName;
        this.certName = certName;
        this.version = version;
        this.request_id = request_id;
        this.requestedDnsSans = requestedDnsSans;
        this.issuer = issuer;
    }

    public String keyName() {
        return keyName;
    }

    public String certName() {
        return certName;
    }

    public int version() {
        return version;
    }

    public Optional<String> request_id() {
        return request_id;
    }

    public Optional<List<String>> requestedDnsSans() {
        return requestedDnsSans;
    }

    public Optional<String> issuer() {
        return issuer;
    }

    public EndpointCertificateMetadata withVersion(int version) {
        return new EndpointCertificateMetadata(
                this.keyName,
                this.certName,
                version,
                this.request_id,
                this.requestedDnsSans,
                this.issuer
        );
    }

    @Override
    public String toString() {
        return "EndpointCertificateMetadata{" +
                "keyName='" + keyName + '\'' +
                ", certName='" + certName + '\'' +
                ", version=" + version +
                ", request_id=" + request_id +
                ", requestedDnsSans=" + requestedDnsSans +
                ", issuer=" + issuer +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCertificateMetadata that = (EndpointCertificateMetadata) o;
        return version == that.version &&
                keyName.equals(that.keyName) &&
                certName.equals(that.certName) &&
                request_id.equals(that.request_id) &&
                requestedDnsSans.equals(that.requestedDnsSans) &&
                issuer.equals(that.issuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyName, certName, version, request_id, requestedDnsSans, issuer);
    }
}
