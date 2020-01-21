// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

public class EndpointCertificateSecrets {
    public static final EndpointCertificateSecrets MISSING = new EndpointCertificateSecrets();

    private final String certificate;
    private final String key;

    private EndpointCertificateSecrets() {
        this(null, null);
    }

    public EndpointCertificateSecrets(String certificate, String key) {
        this.certificate = certificate;
        this.key = key;
    }

    public String certificate() {
        return certificate;
    }

    public String key() {
        return key;
    }

    public boolean isMissing() {
        return this == MISSING;
    }
}
