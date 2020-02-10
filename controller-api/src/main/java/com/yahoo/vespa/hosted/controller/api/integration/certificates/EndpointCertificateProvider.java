// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;

import java.util.List;

/**
 * Generates an endpoint certificate for an application instance.
 *
 * @author andreer
 */
public interface EndpointCertificateProvider  {

    EndpointCertificateMetadata requestCaSignedCertificate(ApplicationId applicationId, List<String> dnsNames);

    List<EndpointCertificateMetadata> listCertificates();
}
