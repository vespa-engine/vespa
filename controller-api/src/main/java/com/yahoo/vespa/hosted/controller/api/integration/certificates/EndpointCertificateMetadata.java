package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.Objects;

/**
 * This class is used for metadata about an application's endpoint certificate on the controller.
 * <p>
 * It is a copy of com.yahoo.config.model.api.EndpointCertificateMetadata, but will soon be extended.
 *
 * @author andreer
 */
public class EndpointCertificateMetadata {

    private final String keyName;
    private final String certName;
    private final int version;

    public EndpointCertificateMetadata(String keyName, String certName, int version) {
        this.keyName = keyName;
        this.certName = certName;
        this.version = version;
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

    @Override
    public String toString() {
        return "EndpointCertificateMetadata{" +
                "keyName='" + keyName + '\'' +
                ", certName='" + certName + '\'' +
                ", version=" + version +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCertificateMetadata that = (EndpointCertificateMetadata) o;
        return version == that.version &&
                Objects.equals(keyName, that.keyName) &&
                Objects.equals(certName, that.certName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyName, certName, version);
    }
}
