package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;

import java.util.Collection;

public interface CertificateManager {

    /**
     * Ensure a valid certificate is provisioned for an application instance in a given environment
     *
     * @param applicationId The application instance for which the certificate is to be provisioned
     * @param environment   The environment for which the certificate is to be provisioned
     * @param endpointNames A collection of endpoint names for which the certificate must be valid
     * @return A reference that allows retrieving the private key and certificate chain from the configured secret store
     */
    CertificateReference provisionTlsCertificate(ApplicationId applicationId, Environment environment, Collection<String> endpointNames);

}
