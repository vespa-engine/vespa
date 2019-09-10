package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;

import java.util.List;

/**
 * Generates a certificate.
 *
 * @author andreer
 */
public interface ApplicationCertificateProvider {

    ApplicationCertificate requestCaSignedCertificate(ApplicationId applicationId, List<String> dnsNames);

}
