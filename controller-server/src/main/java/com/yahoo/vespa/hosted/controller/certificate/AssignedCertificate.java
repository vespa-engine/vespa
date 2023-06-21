// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.certificate;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.util.Optional;

/**
 * Represents a certificate and its owner. A certificate is either assigned to all instances of an application, or a
 * specific one.
 *
 * @author mpolden
 */
public record AssignedCertificate(TenantAndApplicationId application,
                                  Optional<InstanceName> instance,
                                  EndpointCertificateMetadata certificate) {

    public AssignedCertificate with(EndpointCertificateMetadata certificate) {
        return new AssignedCertificate(application, instance, certificate);
    }

}
