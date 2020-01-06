// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
