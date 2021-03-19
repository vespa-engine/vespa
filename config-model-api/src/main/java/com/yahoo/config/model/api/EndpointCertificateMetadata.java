// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

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
}
