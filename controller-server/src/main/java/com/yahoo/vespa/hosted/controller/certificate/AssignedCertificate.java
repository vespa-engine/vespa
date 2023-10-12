// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.certificate;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
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
                                  EndpointCertificate certificate,
                                  boolean shouldValidate) {

    public AssignedCertificate with(EndpointCertificate certificate) {
        return new AssignedCertificate(application, instance, certificate, shouldValidate);
    }

    public AssignedCertificate withoutInstance() {
        return new AssignedCertificate(application, Optional.empty(), certificate, shouldValidate);
    }

    public AssignedCertificate withShouldValidate(boolean shouldValidate) {
        return new AssignedCertificate(application, instance, certificate, shouldValidate);
    }

}
