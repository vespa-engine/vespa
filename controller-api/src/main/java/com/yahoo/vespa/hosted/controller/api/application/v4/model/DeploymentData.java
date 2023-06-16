// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.yolean.concurrent.Memoized;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Data pertaining to a deployment to be done on a config server.
 * Accessor names must match the names in com.yahoo.vespa.config.server.session.PrepareParams.
 *
 * @author jonmv
 */
public class DeploymentData {

    private final ApplicationId instance;
    private final ZoneId zone;
    private final Supplier<InputStream> applicationPackage;
    private final Version platform;
    private final Set<ContainerEndpoint> containerEndpoints;
    private final Supplier<Optional<EndpointCertificateMetadata>> endpointCertificateMetadata;
    private final Optional<DockerImage> dockerImageRepo;
    private final Optional<AthenzDomain> athenzDomain;
    private final Supplier<Quota> quota;
    private final List<TenantSecretStore> tenantSecretStores;
    private final List<X509Certificate> operatorCertificates;
    private final Supplier<Optional<CloudAccount>> cloudAccount;
    private final List<DataplaneTokenVersions> dataPlaneTokens;
    private final boolean dryRun;

    public DeploymentData(ApplicationId instance, ZoneId zone, Supplier<InputStream> applicationPackage, Version platform,
                          Set<ContainerEndpoint> containerEndpoints,
                          Supplier<Optional<EndpointCertificateMetadata>> endpointCertificateMetadata,
                          Optional<DockerImage> dockerImageRepo,
                          Optional<AthenzDomain> athenzDomain,
                          Supplier<Quota> quota,
                          List<TenantSecretStore> tenantSecretStores,
                          List<X509Certificate> operatorCertificates,
                          Supplier<Optional<CloudAccount>> cloudAccount,
                          List<DataplaneTokenVersions> dataPlaneTokens,
                          boolean dryRun) {
        this.instance = requireNonNull(instance);
        this.zone = requireNonNull(zone);
        this.applicationPackage = requireNonNull(applicationPackage);
        this.platform = requireNonNull(platform);
        this.containerEndpoints = Set.copyOf(requireNonNull(containerEndpoints));
        this.endpointCertificateMetadata = new Memoized<>(requireNonNull(endpointCertificateMetadata));
        this.dockerImageRepo = requireNonNull(dockerImageRepo);
        this.athenzDomain = athenzDomain;
        this.quota = new Memoized<>(requireNonNull(quota));
        this.tenantSecretStores = List.copyOf(requireNonNull(tenantSecretStores));
        this.operatorCertificates = List.copyOf(requireNonNull(operatorCertificates));
        this.cloudAccount = new Memoized<>(requireNonNull(cloudAccount));
        this.dataPlaneTokens = dataPlaneTokens;
        this.dryRun = dryRun;
    }

    public ApplicationId instance() {
        return instance;
    }

    public ZoneId zone() {
        return zone;
    }

    public InputStream applicationPackage() {
        return applicationPackage.get();
    }

    public Version platform() {
        return platform;
    }

    public Set<ContainerEndpoint> containerEndpoints() {
        return containerEndpoints;
    }

    public Optional<EndpointCertificateMetadata> endpointCertificateMetadata() {
        return endpointCertificateMetadata.get();
    }

    public Optional<DockerImage> dockerImageRepo() {
        return dockerImageRepo;
    }

    public Optional<AthenzDomain> athenzDomain() {
        return athenzDomain;
    }

    public Quota quota() {
        return quota.get();
    }

    public List<TenantSecretStore> tenantSecretStores() {
        return tenantSecretStores;
    }

    public List<X509Certificate> operatorCertificates() {
        return operatorCertificates;
    }

    public Optional<CloudAccount> cloudAccount() {
        return cloudAccount.get();
    }

    public List<DataplaneTokenVersions> dataPlaneTokens() {
        return dataPlaneTokens;
    }

    public boolean isDryRun() {
        return dryRun;
    }

}
