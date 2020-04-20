package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;

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
    private final Set<ContainerEndpoint> containerEndpoints;
    private final Optional<EndpointCertificateMetadata> endpointCertificateMetadata;
    private final Optional<DockerImage> dockerImageRepo;
    private final Optional<AthenzDomain> athenzDomain;

    public DeploymentData(ApplicationId instance, ZoneId zone, byte[] applicationPackage, Version platform,
                          Set<ContainerEndpoint> containerEndpoints,
                          Optional<EndpointCertificateMetadata> endpointCertificateMetadata,
                          Optional<DockerImage> dockerImageRepo,
                          Optional<AthenzDomain> athenzDomain) {
        this.instance = requireNonNull(instance);
        this.zone = requireNonNull(zone);
        this.applicationPackage = requireNonNull(applicationPackage);
        this.platform = requireNonNull(platform);
        this.containerEndpoints = requireNonNull(containerEndpoints);
        this.endpointCertificateMetadata = requireNonNull(endpointCertificateMetadata);
        this.dockerImageRepo = requireNonNull(dockerImageRepo);
        this.athenzDomain = athenzDomain;
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

    public Set<ContainerEndpoint> containerEndpoints() {
        return containerEndpoints;
    }

    public Optional<EndpointCertificateMetadata> endpointCertificateMetadata() {
        return endpointCertificateMetadata;
    }

    public Optional<DockerImage> dockerImageRepo() {
        return dockerImageRepo;
    }

    public Optional<AthenzDomain> athenzDomain() {
        return athenzDomain;
    }
}
