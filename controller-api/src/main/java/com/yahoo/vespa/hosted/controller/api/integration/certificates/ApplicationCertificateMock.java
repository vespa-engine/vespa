// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;

import java.util.UUID;

/**
 * @author tokle
 */
public class ApplicationCertificateMock implements ApplicationCertificateProvider {

    @Override
    public ApplicationCertificate requestCaSignedCertificate(ApplicationId applicationId) {
        return new ApplicationCertificate(String.format("vespa.tls.%s.%s@%s", applicationId.tenant(),
                                                        applicationId.application(),
                                                        UUID.randomUUID().toString()));
    }

}
