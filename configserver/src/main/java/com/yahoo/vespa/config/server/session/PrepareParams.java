// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointSerializer;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataSerializer;
import com.yahoo.vespa.config.server.tenant.TenantSecretStoreSerializer;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parameters for preparing an application. Immutable.
 *
 * @author Ulf Lilleengen
 */
public final class PrepareParams {

    static final String APPLICATION_NAME_PARAM_NAME = "applicationName";
    static final String INSTANCE_PARAM_NAME = "instance";
    static final String IGNORE_VALIDATION_PARAM_NAME = "ignoreValidationErrors";
    static final String DRY_RUN_PARAM_NAME = "dryRun";
    static final String VERBOSE_PARAM_NAME = "verbose";
    static final String VESPA_VERSION_PARAM_NAME = "vespaVersion";
    static final String CONTAINER_ENDPOINTS_PARAM_NAME = "containerEndpoints";
    static final String ENDPOINT_CERTIFICATE_METADATA_PARAM_NAME = "endpointCertificateMetadata";
    static final String DOCKER_IMAGE_REPOSITORY = "dockerImageRepository";
    static final String ATHENZ_DOMAIN = "athenzDomain";
    static final String APPLICATION_HOST_ROLE = "applicationHostRole";
    static final String APPLICATION_CONTAINER_ROLE = "applicationContainerRole";
    static final String QUOTA_PARAM_NAME = "quota";
    static final String TENANT_SECRET_STORES_PARAM_NAME = "tenantSecretStores";
    static final String FORCE_PARAM_NAME = "force";
    static final String WAIT_FOR_RESOURCES_IN_PREPARE = "waitForResourcesInPrepare";

    private final ApplicationId applicationId;
    private final TimeoutBudget timeoutBudget;
    private final boolean ignoreValidationErrors;
    private final boolean dryRun;
    private final boolean verbose;
    private final boolean isBootstrap;
    private final boolean force;
    private final boolean waitForResourcesInPrepare;
    private final Optional<Version> vespaVersion;
    private final List<ContainerEndpoint> containerEndpoints;
    private final Optional<EndpointCertificateMetadata> endpointCertificateMetadata;
    private final Optional<DockerImage> dockerImageRepository;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<ApplicationRoles> applicationRoles;
    private final Optional<Quota> quota;
    private final List<TenantSecretStore> tenantSecretStores;

    private PrepareParams(ApplicationId applicationId, TimeoutBudget timeoutBudget, boolean ignoreValidationErrors,
                          boolean dryRun, boolean verbose, boolean isBootstrap, Optional<Version> vespaVersion,
                          List<ContainerEndpoint> containerEndpoints,
                          Optional<EndpointCertificateMetadata> endpointCertificateMetadata,
                          Optional<DockerImage> dockerImageRepository, Optional<AthenzDomain> athenzDomain,
                          Optional<ApplicationRoles> applicationRoles, Optional<Quota> quota, List<TenantSecretStore> tenantSecretStores,
                          boolean force, boolean waitForResourcesInPrepare) {
        this.timeoutBudget = timeoutBudget;
        this.applicationId = Objects.requireNonNull(applicationId);
        this.ignoreValidationErrors = ignoreValidationErrors;
        this.dryRun = dryRun;
        this.verbose = verbose;
        this.isBootstrap = isBootstrap;
        this.vespaVersion = vespaVersion;
        this.containerEndpoints = containerEndpoints;
        this.endpointCertificateMetadata = endpointCertificateMetadata;
        this.dockerImageRepository = dockerImageRepository;
        this.athenzDomain = athenzDomain;
        this.applicationRoles = applicationRoles;
        this.quota = quota;
        this.tenantSecretStores = tenantSecretStores;
        this.force = force;
        this.waitForResourcesInPrepare = waitForResourcesInPrepare;
    }

    public static class Builder {

        private boolean ignoreValidationErrors = false;
        private boolean dryRun = false;
        private boolean verbose = false;
        private boolean isBootstrap = false;
        private boolean force = false;
        private boolean waitForResourcesInPrepare = false;
        private ApplicationId applicationId = null;
        private TimeoutBudget timeoutBudget = new TimeoutBudget(Clock.systemUTC(), Duration.ofSeconds(60));
        private Optional<Version> vespaVersion = Optional.empty();
        private List<ContainerEndpoint> containerEndpoints = null;
        private Optional<EndpointCertificateMetadata> endpointCertificateMetadata = Optional.empty();
        private Optional<DockerImage> dockerImageRepository = Optional.empty();
        private Optional<AthenzDomain> athenzDomain = Optional.empty();
        private Optional<ApplicationRoles> applicationRoles = Optional.empty();
        private Optional<Quota> quota = Optional.empty();
        private List<TenantSecretStore> tenantSecretStores = List.of();

        public Builder() { }

        public Builder applicationId(ApplicationId applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public Builder ignoreValidationErrors(boolean ignoreValidationErrors) {
            this.ignoreValidationErrors = ignoreValidationErrors;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder isBootstrap(boolean isBootstrap) {
            this.isBootstrap = isBootstrap;
            return this;
        }

        public Builder timeoutBudget(TimeoutBudget timeoutBudget) {
            this.timeoutBudget = timeoutBudget;
            return this;
        }

        public Builder vespaVersion(String vespaVersion) {
            Optional<Version> version = Optional.empty();
            if (vespaVersion != null && !vespaVersion.isEmpty()) {
                version = Optional.of(Version.fromString(vespaVersion));
            }
            this.vespaVersion = version;
            return this;
        }

        public Builder vespaVersion(Version vespaVersion) {
            this.vespaVersion = Optional.ofNullable(vespaVersion);
            return this;
        }

        public Builder containerEndpoints(String serialized) {
            this.containerEndpoints = (serialized == null)
                    ? List.of()
                    : ContainerEndpointSerializer.endpointListFromSlime(SlimeUtils.jsonToSlime(serialized));
            return this;
        }

        public Builder endpointCertificateMetadata(String serialized) {
            this.endpointCertificateMetadata = (serialized == null)
                    ? Optional.empty()
                    : Optional.of(EndpointCertificateMetadataSerializer.fromSlime(SlimeUtils.jsonToSlime(serialized).get()));
            return this;
        }

        public Builder dockerImageRepository(String dockerImageRepository) {
            this.dockerImageRepository = (dockerImageRepository == null)
                    ? Optional.empty()
                    : Optional.of(DockerImage.fromString(dockerImageRepository));
            return this;
        }

        public Builder dockerImageRepository(DockerImage dockerImageRepository) {
            this.dockerImageRepository = Optional.ofNullable(dockerImageRepository);
            return this;
        }

        public Builder athenzDomain(String athenzDomain) {
            this.athenzDomain = Optional.ofNullable(athenzDomain).map(AthenzDomain::from);
            return this;
        }

        public Builder athenzDomain(AthenzDomain athenzDomain) {
            this.athenzDomain = Optional.ofNullable(athenzDomain);
            return this;
        }

        public Builder applicationRoles(ApplicationRoles applicationRoles) {
            this.applicationRoles = Optional.ofNullable(applicationRoles);
            return this;
        }

        public Builder quota(String serialized) {
            this.quota = (serialized == null)
                    ? Optional.empty()
                    : Optional.of(Quota.fromSlime(SlimeUtils.jsonToSlime(serialized).get()));
            return this;
        }

        public Builder tenantSecretStores(String serialized) {
            List<TenantSecretStore> secretStores = (serialized == null)
                    ? List.of()
                    : TenantSecretStoreSerializer.listFromSlime(SlimeUtils.jsonToSlime(serialized).get());
            return tenantSecretStores(secretStores);
        }

        public Builder tenantSecretStores(List<TenantSecretStore> tenantSecretStores) {
            this.tenantSecretStores = tenantSecretStores;
            return this;
        }

        public Builder waitForResourcesInPrepare(boolean waitForResourcesInPrepare) {
            this.waitForResourcesInPrepare = waitForResourcesInPrepare;
            return this;
        }

        public Builder force(boolean force) {
            this.force = force;
            return this;
        }

        public PrepareParams build() {
            return new PrepareParams(applicationId, timeoutBudget, ignoreValidationErrors, dryRun,
                                     verbose, isBootstrap, vespaVersion, containerEndpoints,
                                     endpointCertificateMetadata, dockerImageRepository, athenzDomain,
                                     applicationRoles, quota, tenantSecretStores, force, waitForResourcesInPrepare);
        }
    }

    public static PrepareParams fromHttpRequest(HttpRequest request, TenantName tenant, Duration barrierTimeout) {
        return new Builder().ignoreValidationErrors(request.getBooleanProperty(IGNORE_VALIDATION_PARAM_NAME))
                            .dryRun(request.getBooleanProperty(DRY_RUN_PARAM_NAME))
                            .verbose(request.getBooleanProperty(VERBOSE_PARAM_NAME))
                            .timeoutBudget(SessionHandler.getTimeoutBudget(request, barrierTimeout))
                            .applicationId(createApplicationId(request, tenant))
                            .vespaVersion(request.getProperty(VESPA_VERSION_PARAM_NAME))
                            .containerEndpoints(request.getProperty(CONTAINER_ENDPOINTS_PARAM_NAME))
                            .endpointCertificateMetadata(request.getProperty(ENDPOINT_CERTIFICATE_METADATA_PARAM_NAME))
                            .dockerImageRepository(request.getProperty(DOCKER_IMAGE_REPOSITORY))
                            .athenzDomain(request.getProperty(ATHENZ_DOMAIN))
                            .applicationRoles(ApplicationRoles.fromString(request.getProperty(APPLICATION_HOST_ROLE), request.getProperty(APPLICATION_CONTAINER_ROLE)))
                            .quota(request.getProperty(QUOTA_PARAM_NAME))
                            .tenantSecretStores(request.getProperty(TENANT_SECRET_STORES_PARAM_NAME))
                            .force(request.getBooleanProperty(FORCE_PARAM_NAME))
                            .waitForResourcesInPrepare(request.getBooleanProperty(WAIT_FOR_RESOURCES_IN_PREPARE))
                            .build();
    }

    private static ApplicationId createApplicationId(HttpRequest request, TenantName tenant) {
        return new ApplicationId.Builder()
               .tenant(tenant)
               .applicationName(getPropertyWithDefault(request, APPLICATION_NAME_PARAM_NAME, "default"))
               .instanceName(getPropertyWithDefault(request, INSTANCE_PARAM_NAME, "default"))
               .build();
    }

    private static String getPropertyWithDefault(HttpRequest request, String propertyName, String defaultProperty) {
        return getProperty(request, propertyName).orElse(defaultProperty);
    }

    private static Optional<String> getProperty(HttpRequest request, String propertyName) {
        return Optional.ofNullable(request.getProperty(propertyName));
    }
    
    public String getApplicationName() {
        return applicationId.application().value();
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    /** Returns the Vespa version the nodes running the prepared system should have, or empty to use the system version */
    public Optional<Version> vespaVersion() { return vespaVersion; }

    /** Returns the container endpoints that should be made available for this deployment. One per cluster */
    public List<ContainerEndpoint> containerEndpoints() {
        return containerEndpoints;
    }

    public boolean ignoreValidationErrors() {
        return ignoreValidationErrors;
    }

    public boolean isDryRun() { return dryRun; }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isBootstrap() { return isBootstrap; }

    public boolean force() { return force; }

    public boolean waitForResourcesInPrepare() { return waitForResourcesInPrepare; }

    public TimeoutBudget getTimeoutBudget() {
        return timeoutBudget;
    }

    public Optional<EndpointCertificateMetadata> endpointCertificateMetadata() {
        return endpointCertificateMetadata;
    }

    public Optional<DockerImage> dockerImageRepository() {
        return dockerImageRepository;
    }

    public Optional<AthenzDomain> athenzDomain() { return athenzDomain; }

    public Optional<ApplicationRoles> applicationRoles() {
        return applicationRoles;
    }

    public Optional<Quota> quota() {
        return quota;
    }

    public List<TenantSecretStore> tenantSecretStores() {
        return tenantSecretStores;
    }
}
