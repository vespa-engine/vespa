package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Data pertaining to a deployment to be done on a config server.
 *
 * @author jonmv
 */
public class DeploymentData {

    private final ApplicationId instance;
    private final ZoneId zone;
    private final byte[] applicationPackage;
    private final Version platform;
    private final boolean ignoreValidationErrors;
    private final Set<ContainerEndpoint> containerEndpoints;
    private final Optional<EndpointCertificateMetadata> endpointCertificateMetadata;

    public DeploymentData(ApplicationId instance, ZoneId zone, byte[] applicationPackage, Version platform,
                          boolean ignoreValidationErrors, Set<ContainerEndpoint> containerEndpoints,
                          Optional<EndpointCertificateMetadata> endpointCertificateMetadata) {
        this.instance = requireNonNull(instance);
        this.zone = requireNonNull(zone);
        this.applicationPackage = requireNonNull(applicationPackage);
        this.platform = requireNonNull(platform);
        this.ignoreValidationErrors = requireNonNull(ignoreValidationErrors);
        this.containerEndpoints = requireNonNull(containerEndpoints);
        this.endpointCertificateMetadata = requireNonNull(endpointCertificateMetadata);
    }

    public ApplicationId instance() {
        return instance;
    }

    public ZoneId zone() {
        return zone;
    }

    public byte[] applicationPackage() {
        return applicationPackage;
    }

    public Version platform() {
        return platform;
    }

    public boolean ignoreValidationErrors() {
        return ignoreValidationErrors;
    }

    public Set<ContainerEndpoint> containerEndpoints() {
        return containerEndpoints;
    }

    public Optional<EndpointCertificateMetadata> endpointCertificateMetadata() {
        return endpointCertificateMetadata;
    }

}
