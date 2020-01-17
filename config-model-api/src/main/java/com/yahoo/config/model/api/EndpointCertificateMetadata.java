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
}
