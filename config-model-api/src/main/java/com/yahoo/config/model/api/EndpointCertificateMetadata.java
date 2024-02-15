// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Objects;

public class EndpointCertificateMetadata {

    public enum Provider { digicert, globalsign, zerossl }
    private final String keyName;
    private final String certName;
    private final int version;
    private final Provider issuer;

    public EndpointCertificateMetadata(String keyName, String certName, int version, Provider issuer) {
        this.keyName = keyName;
        this.certName = certName;
        this.version = version;
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

    public Provider issuer() {
        return issuer;
    }

    @Override
    public String toString() {
        return "EndpointCertificateMetadata{" +
                "keyName='" + keyName + '\'' +
                ", certName='" + certName + '\'' +
                ", version=" + version +
                ", issuer='" + issuer + '\'' +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCertificateMetadata that = (EndpointCertificateMetadata) o;
        return version == that.version && Objects.equals(keyName, that.keyName) && Objects.equals(certName, that.certName) && Objects.equals(issuer, that.issuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyName, certName, version, issuer);
    }
}
