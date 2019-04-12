package com.yahoo.vespa.hosted.controller.api.integration.certificates;

public class CertificateReference {
    public CertificateReference(String secretStorePrivateKeyname, int secretStorePrivateKeyVersion, String secretStorePublicCertificateName, int secretStorePublicCertificateVersion) {
        this.secretStorePrivateKeyname = secretStorePrivateKeyname;
        this.secretStorePrivateKeyVersion = secretStorePrivateKeyVersion;
        this.secretStorePublicCertificateName = secretStorePublicCertificateName;
        this.secretStorePublicCertificateVersion = secretStorePublicCertificateVersion;
    }

    private String secretStorePrivateKeyname;
    private int secretStorePrivateKeyVersion;

    private String secretStorePublicCertificateName;
    private int secretStorePublicCertificateVersion;
}
