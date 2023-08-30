// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The endpoints and their certificate (if any) of a deployment.
 *
 * @author mpolden
 */
public record DeploymentEndpoints(Set<ContainerEndpoint> endpoints, Optional<EndpointCertificate> certificate) {

    public static final DeploymentEndpoints none = new DeploymentEndpoints(Set.of(), Optional.empty());

    public DeploymentEndpoints {
        Objects.requireNonNull(endpoints);
        Objects.requireNonNull(certificate);
    }

}
