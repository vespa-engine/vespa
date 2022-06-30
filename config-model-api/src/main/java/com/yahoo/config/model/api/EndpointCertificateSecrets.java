// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

public class EndpointCertificateSecrets {
    public static final EndpointCertificateSecrets MISSING = new EndpointCertificateSecrets();
    private final String certificate;
    private final String key;
    private final int version;

    private EndpointCertificateSecrets() {
        this(null, null);
    }

    public EndpointCertificateSecrets(String certificate, String key) {
        this.certificate = certificate;
        this.key = key;
        this.version = -1;
    }

    public EndpointCertificateSecrets(String certificate, String key, int version) {
        this.certificate = certificate;
        this.key = key;
        this.version = version;
    }

    public String certificate() {
        return certificate;
    }

    public String key() {
        return key;
    }

    public int version() {
        return version;
    }

    public static EndpointCertificateSecrets missing(int version) {
        return new EndpointCertificateSecrets(null, null, version);
    }

    public boolean isMissing() {
        return this == MISSING || certificate == null || key == null;
    }
}
