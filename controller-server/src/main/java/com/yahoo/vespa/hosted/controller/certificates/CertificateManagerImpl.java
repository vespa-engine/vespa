package com.yahoo.vespa.hosted.controller.certificates;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.CertificateManager;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.CertificateReference;

import java.util.Collection;

public class CertificateManagerImpl implements CertificateManager {
    @Override
    public CertificateReference provisionTlsCertificate(ApplicationId applicationId, Environment environment, Collection<String> endpointNames) {
        return new CertificateReference("keyname", 1, "certname", 1);
    }
}
