package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;

public interface CertificateProvider {
    List<X509Certificate> requestCaSignedCertificate(KeyPair keyPair, List<String> domains);
}
