// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.log.LogLevel;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeploymentData;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.RestartFilter;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.application.ActivateResult;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageValidator;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.DeploymentQuotaCalculator;
import com.yahoo.vespa.hosted.controller.application.QuotaUsage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.certificate.EndpointCertificates;
import com.yahoo.vespa.hosted.controller.concurrent.Once;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccessGrant;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.active;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.reserved;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * A singleton owned by the Controller which contains the methods and state for controlling applications.
 *
 * @author bratseth
 */
public class ApplicationController {

    private static final Logger log = Logger.getLogger(ApplicationController.class.getName());

    /** The controller owning this */
    private final Controller controller;

    /** For persistence */
    private final CuratorDb curator;

    private final ArtifactRepository artifactRepository;
    private final ApplicationStore applicationStore;
    private final AccessControl accessControl;
    private final ConfigServer configServer;
    private final Clock clock;
    private final DeploymentTrigger deploymentTrigger;
    private final ApplicationPackageValidator applicationPackageValidator;
    private final EndpointCertificates endpointCertificates;
    private final StringFlag dockerImageRepoFlag;
    private final BillingController billingController;

    ApplicationController(Controller controller, CuratorDb curator, AccessControl accessControl, Clock clock,
                          FlagSource flagSource, BillingController billingController) {

        this.controller = controller;
        this.curator = curator;
        this.accessControl = accessControl;
        this.configServer = controller.serviceRegistry().configServer();
        this.clock = clock;
        this.artifactRepository = controller.serviceRegistry().artifactRepository();
        this.applicationStore = controller.serviceRegistry().applicationStore();
        this.dockerImageRepoFlag = PermanentFlags.DOCKER_IMAGE_REPO.bindTo(flagSource);
        this.billingController = billingController;

        deploymentTrigger = new DeploymentTrigger(controller, clock);
        applicationPackageValidator = new ApplicationPackageValidator(controller);
        endpointCertificates = new EndpointCertificates(controller,
                                                        controller.serviceRegistry().endpointCertificateProvider(),
                                                        controller.serviceRegistry().endpointCertificateValidator());

        // Update serialization format of all applications
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = clock.instant();
            int count = 0;
            for (TenantAndApplicationId id : curator.readApplicationIds()) {
                lockApplicationIfPresent(id, application -> {
                    for (InstanceName instance : application.get().deploymentSpec().instanceNames())
                        if (!application.get().instances().containsKey(instance))
                            application = withNewInstance(application, id.instance(instance));
                    store(application);
                });
                count++;
            }
            log.log(Level.INFO, Text.format("Wrote %d applications in %s", count,
                                              Duration.between(start, clock.instant())));
        });
    }

    /** Returns the application with the given id, or null if it is not present */
    public Optional<Application> getApplication(TenantAndApplicationId id) {
        return curator.readApplication(id);
    }

    /** Returns the instance with the given id, or null if it is not present */
    // TODO jonmv: remove or inline
    public Optional<Instance> getInstance(ApplicationId id) {
        return getApplication(TenantAndApplicationId.from(id)).flatMap(application -> application.get(id.instance()));
    }

    /**
     * Triggers reindexing for the given document types in the given clusters, for the given application.
     *
     * If no clusters are given, reindexing is triggered for the entire application; otherwise
     * if no documents types are given, reindexing is triggered for all given clusters; otherwise
     * reindexing is triggered for the cartesian product of the given clusters and document types.
     */
    public void reindex(ApplicationId id, ZoneId zoneId, List<String> clusterNames, List<String> documentTypes, boolean indexedOnly) {
        configServer.reindex(new DeploymentId(id, zoneId), clusterNames, documentTypes, indexedOnly);
    }

    /** Returns the reindexing status for the given application in the given zone. */
    public ApplicationReindexing applicationReindexing(ApplicationId id, ZoneId zoneId) {
        return configServer.getReindexing(new DeploymentId(id, zoneId));
    }

    /** Enables reindexing for the given application in the given zone. */
    public void enableReindexing(ApplicationId id, ZoneId zoneId) {
        configServer.enableReindexing(new DeploymentId(id, zoneId));
    }

    /** Disables reindexing for the given application in the given zone. */
    public void disableReindexing(ApplicationId id, ZoneId zoneId) {
        configServer.disableReindexing(new DeploymentId(id, zoneId));
    }

    /**
     * Returns the application with the given id
     *
     * @throws IllegalArgumentException if it does not exist
     */
    public Application requireApplication(TenantAndApplicationId id) {
        return getApplication(id).orElseThrow(() -> new IllegalArgumentException(id + " not found"));
    }

    /**
     * Returns the instance with the given id
     *
     * @throws IllegalArgumentException if it does not exist
     */
    // TODO jonvm: remove or inline
    public Instance requireInstance(ApplicationId id) {
        return getInstance(id).orElseThrow(() -> new IllegalArgumentException(id + " not found"));
    }

    /** Returns a snapshot of all applications */
    public List<Application> asList() {
        return curator.readApplications(false);
    }

    /**
     * Returns a snapshot of all readable applications. Unlike {@link ApplicationController#asList()} this ignores
     * applications that cannot currently be read (e.g. due to serialization issues) and may return an incomplete
     * snapshot.
     *
     * This should only be used in cases where acting on a subset of applications is better than none.
     */
    public List<Application> readable() {
        return curator.readApplications(true);
    }

    /** Returns the ID of all known applications. */
    public List<TenantAndApplicationId> idList() {
        return curator.readApplicationIds();
    }

    /** Returns a snapshot of all applications of a tenant */
    public List<Application> asList(TenantName tenant) {
        return curator.readApplications(tenant);
    }

    public ArtifactRepository artifacts() { return artifactRepository; }

    public ApplicationStore applicationStore() {  return applicationStore; }

    /** Returns all currently reachable content clusters among the given deployments. */
    public Map<ZoneId, List<String>> reachableContentClustersByZone(Collection<DeploymentId> ids) {
        Map<ZoneId, List<String>> clusters = new TreeMap<>(Comparator.comparing(ZoneId::value));
        for (DeploymentId id : ids)
            if (isHealthy(id))
                clusters.put(id.zoneId(), List.copyOf(configServer.getContentClusters(id)));

        return Collections.unmodifiableMap(clusters);
    }

    /** Reads the oldest installed platform for the given application and zone from job history, or a node repo. */
    private Optional<Version> oldestInstalledPlatform(JobStatus job) {
        Version oldest = null;
        for (Run run : job.runs().descendingMap().values()) {
            Version version = run.versions().targetPlatform();
            if (oldest == null || version.isBefore(oldest))
                oldest = version;

            if (run.status() == RunStatus.success)
                return Optional.of(oldest);
        }
        // If no successful run was found, ask the node repository in the relevant zone.
        return oldestInstalledPlatform(job.id());
    }

    /** Reads the oldest installed platform for the given application and zone from the node repo of that zone. */
    private Optional<Version> oldestInstalledPlatform(JobId job) {
        return configServer.nodeRepository().list(job.type().zone(controller.system()),
                                                  NodeFilter.all()
                                                            .applications(job.application())
                                                            .states(active, reserved))
                           .stream()
                           .map(Node::currentVersion)
                           .filter(version -> ! version.isEmpty())
                           .min(naturalOrder());
    }

    /** Returns the oldest Vespa version installed on any active or reserved production node for the given application. */
    public Version oldestInstalledPlatform(TenantAndApplicationId id) {
        return controller.jobController().deploymentStatus(requireApplication(id)).jobs()
                         .production().asList().stream()
                         .map(this::oldestInstalledPlatform)
                         .flatMap(Optional::stream)
                         .min(naturalOrder())
                         .orElse(controller.readSystemVersion());
    }

    /**
     * Creates a new application for an existing tenant.
     *
     * @throws IllegalArgumentException if the application already exists
     */
    public Application createApplication(TenantAndApplicationId id, Credentials credentials) {
        try (Lock lock = lock(id)) {
            if (getApplication(id).isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': Application already exists");
            if (getApplication(dashToUnderscore(id)).isPresent()) // VESPA-1945
                throw new IllegalArgumentException("Could not create '" + id + "': Application " + dashToUnderscore(id) + " already exists");

            com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId.validate(id.application().value());

            if (controller.tenants().get(id.tenant()).isEmpty())
                throw new IllegalArgumentException("Could not create '" + id + "': This tenant does not exist");
            accessControl.createApplication(id, credentials);

            LockedApplication locked = new LockedApplication(new Application(id, clock.instant()), lock);
            store(locked);
            log.info("Created " + locked);
            return locked.get();
        }
    }

    /**
     * Creates a new instance for an existing application.
     *
     * @throws IllegalArgumentException if the instance already exists, or has an invalid instance name.
     */
    public void createInstance(ApplicationId id) {
        lockApplicationOrThrow(TenantAndApplicationId.from(id), application -> {
            store(withNewInstance(application, id));
        });
    }

    /** Returns given application with a new instance */
    public LockedApplication withNewInstance(LockedApplication application, ApplicationId instance) {
        if (instance.instance().isTester())
            throw new IllegalArgumentException("'" + instance + "' is a tester application!");
        InstanceId.validate(instance.instance().value());

        if (getInstance(instance).isPresent())
            throw new IllegalArgumentException("Could not create '" + instance + "': Instance already exists");
        if (getInstance(dashToUnderscore(instance)).isPresent()) // VESPA-1945
            throw new IllegalArgumentException("Could not create '" + instance + "': Instance " + dashToUnderscore(instance) + " already exists");

        log.info("Created " + instance);
        return application.withNewInstance(instance.instance());
    }

    /** Deploys an application package for an existing application instance. */
    public ActivateResult deploy(JobId job, boolean deploySourceVersions) {
        if (job.application().instance().isTester())
            throw new IllegalArgumentException("'" + job.application() + "' is a tester application!");

        TenantAndApplicationId applicationId = TenantAndApplicationId.from(job.application());
        ZoneId zone = job.type().zone(controller.system());

        try (Lock deploymentLock = lockForDeployment(job.application(), zone)) {
            Set<ContainerEndpoint> containerEndpoints;
            Optional<EndpointCertificateMetadata> endpointCertificateMetadata;

            Run run = controller.jobController().last(job)
                                .orElseThrow(() -> new IllegalStateException("No known run of '" + job + "'"));

            if (run.hasEnded())
                throw new IllegalStateException("No deployment expected for " + job + " now, as no job is running");

            Version platform = run.versions().sourcePlatform().filter(__ -> deploySourceVersions).orElse(run.versions().targetPlatform());
            ApplicationVersion revision = run.versions().sourceApplication().filter(__ -> deploySourceVersions).orElse(run.versions().targetApplication());
            ApplicationPackage applicationPackage = new ApplicationPackage(applicationStore.get(new DeploymentId(job.application(), zone), revision));

            try (Lock lock = lock(applicationId)) {
                LockedApplication application = new LockedApplication(requireApplication(applicationId), lock);
                Instance instance = application.get().require(job.application().instance());
                rejectOldChange(instance, platform, revision, job, zone);

                if (   ! applicationPackage.trustedCertificates().isEmpty()
                    &&   run.testerCertificate().isPresent())
                    applicationPackage = applicationPackage.withTrustedCertificate(run.testerCertificate().get());

                endpointCertificateMetadata = endpointCertificates.getMetadata(instance, zone, applicationPackage.deploymentSpec().instance(instance.name()));

                containerEndpoints = controller.routing().containerEndpointsOf(application.get(), job.application().instance(), zone);

            } // Release application lock while doing the deployment, which is a lengthy task.

            // Carry out deployment without holding the application lock.
            ActivateResult result = deploy(job.application(), applicationPackage, zone, platform, containerEndpoints, endpointCertificateMetadata);

            // Record the quota usage for this application
            var quotaUsage = deploymentQuotaUsage(zone, job.application());

            // For direct deployments use the full deployment ID, but otherwise use just the tenant and application as
            // the source since it's the same application, so it should have the same warnings
            NotificationSource source = zone.environment().isManuallyDeployed() ?
                    NotificationSource.from(new DeploymentId(job.application(), zone)) : NotificationSource.from(applicationId);
            List<String> warnings = Optional.ofNullable(result.prepareResponse().log)
                    .map(logs -> logs.stream()
                            .filter(log -> log.applicationPackage)
                            .filter(log -> LogLevel.parse(log.level).intValue() >= Level.WARNING.intValue())
                            .map(log -> log.message)
                            .sorted()
                            .distinct()
                            .collect(Collectors.toList()))
                    .orElseGet(List::of);
            if (warnings.isEmpty()) controller.notificationsDb().removeNotification(source, Notification.Type.applicationPackage);
            else controller.notificationsDb().setNotification(source, Notification.Type.applicationPackage, Notification.Level.warning, warnings);

            lockApplicationOrThrow(applicationId, application ->
                    store(application.with(job.application().instance(),
                                           instance -> instance.withNewDeployment(zone, revision, platform,
                                                                                  clock.instant(), warningsFrom(result),
                                                                                  quotaUsage))));
            return result;
        }
    }

    /** Stores the deployment spec and validation overrides from the application package, and runs cleanup. */
    public LockedApplication storeWithUpdatedConfig(LockedApplication application, ApplicationPackage applicationPackage) {
        applicationPackageValidator.validate(application.get(), applicationPackage, clock.instant());

        application = application.with(applicationPackage.deploymentSpec());
        application = application.with(applicationPackage.validationOverrides());

        var existingInstances = application.get().instances().keySet();
        var declaredInstances = applicationPackage.deploymentSpec().instanceNames();
        for (var name : declaredInstances)
            if ( ! existingInstances.contains(name))
                application = withNewInstance(application, application.get().id().instance(name));

        // Delete zones not listed in DeploymentSpec, if allowed
        // We do this at deployment time for externally built applications, and at submission time
        // for internally built ones, to be able to return a validation failure message when necessary
        for (InstanceName name : existingInstances) {
            application = withoutDeletedDeployments(application, name);
        }

        for (InstanceName instance : declaredInstances)
            if (applicationPackage.deploymentSpec().requireInstance(instance).concerns(Environment.prod))
                application = controller.routing().assignRotations(application, instance);

        // Validate new deployment spec thoroughly before storing it.
        controller.jobController().deploymentStatus(application.get());

        // Clear notifications for instances that are no longer declared
        for (var name : existingInstances)
            if ( ! declaredInstances.contains(name))
                controller.notificationsDb().removeNotifications(NotificationSource.from(application.get().id().instance(name)));

        store(application);
        return application;
    }

    /** Deploy a system application to given zone */
    public void deploy(SystemApplication application, ZoneId zone, Version version) {
        if (application.hasApplicationPackage()) {
            deploySystemApplicationPackage(application, zone, version);
        } else {
            // Deploy by calling node repository directly
            configServer.nodeRepository().upgrade(zone, application.nodeType(), version);
        }
    }

    /** Deploy a system application to given zone */
    public ActivateResult deploySystemApplicationPackage(SystemApplication application, ZoneId zone, Version version) {
        if (application.hasApplicationPackage()) {
            ApplicationPackage applicationPackage = new ApplicationPackage(
                    artifactRepository.getSystemApplicationPackage(application.id(), zone, version)
            );
            return deploy(application.id(), applicationPackage, zone, version, Set.of(), /* No application cert */ Optional.empty());
        } else {
           throw new RuntimeException("This system application does not have an application package: " + application.id().toShortString());
        }
    }

    /** Deploys the given tester application to the given zone. */
    public ActivateResult deployTester(TesterId tester, ApplicationPackage applicationPackage, ZoneId zone, Version platform) {
        return deploy(tester.id(), applicationPackage, zone, platform, Set.of(), /* No application cert for tester*/ Optional.empty());
    }

    private ActivateResult deploy(ApplicationId application, ApplicationPackage applicationPackage,
                                  ZoneId zone, Version platform, Set<ContainerEndpoint> endpoints,
                                  Optional<EndpointCertificateMetadata> endpointCertificateMetadata) {
        try {
            Optional<DockerImage> dockerImageRepo = Optional.ofNullable(
                    dockerImageRepoFlag
                            .with(FetchVector.Dimension.ZONE_ID, zone.value())
                            .with(FetchVector.Dimension.APPLICATION_ID, application.serializedForm())
                            .value())
                    .filter(s -> !s.isBlank())
                    .map(DockerImage::fromString);

            Optional<AthenzDomain> domain = controller.tenants().get(application.tenant())
                    .filter(tenant-> tenant instanceof AthenzTenant)
                    .map(tenant -> ((AthenzTenant)tenant).domain());

            if (zone.environment().isManuallyDeployed())
                controller.applications().applicationStore().putMeta(new DeploymentId(application, zone),
                                                                     clock.instant(),
                                                                     applicationPackage.metaDataZip());

            Quota deploymentQuota = DeploymentQuotaCalculator.calculate(billingController.getQuota(application.tenant()),
                    asList(application.tenant()), application, zone, applicationPackage.deploymentSpec());

            List<TenantSecretStore> tenantSecretStores = controller.tenants()
                    .get(application.tenant())
                    .filter(tenant-> tenant instanceof CloudTenant)
                    .map(tenant -> ((CloudTenant) tenant).tenantSecretStores())
                    .orElse(List.of());
            List<X509Certificate> operatorCertificates = controller.supportAccess().activeGrantsFor(new DeploymentId(application, zone)).stream()
                    .map(SupportAccessGrant::certificate)
                    .collect(toList());

            ConfigServer.PreparedApplication preparedApplication =
                    configServer.deploy(new DeploymentData(application, zone, applicationPackage.zippedContent(), platform,
                                                           endpoints, endpointCertificateMetadata, dockerImageRepo, domain,
                                                           deploymentQuota, tenantSecretStores, operatorCertificates));

            return new ActivateResult(new RevisionId(applicationPackage.hash()), preparedApplication.prepareResponse(),
                                      applicationPackage.zippedContent().length);
        } finally {
            // Even if prepare fails, a load balancer may have been provisioned. Always refresh routing policies so that
            // any DNS updates can be propagated as early as possible.
            controller.routing().policies().refresh(application, applicationPackage.deploymentSpec(), zone);
        }
    }

    private LockedApplication withoutDeletedDeployments(LockedApplication application, InstanceName instance) {
        DeploymentSpec deploymentSpec = application.get().deploymentSpec();
        List<ZoneId> deploymentsToRemove = application.get().require(instance).productionDeployments().values().stream()
                                                      .map(Deployment::zone)
                                                      .filter(zone ->      deploymentSpec.instance(instance).isEmpty()
                                                                      || ! deploymentSpec.requireInstance(instance).deploysTo(zone.environment(),
                                                                                                                              zone.region()))
                                                      .collect(toList());

        if (deploymentsToRemove.isEmpty())
            return application;

        if ( ! application.get().validationOverrides().allows(ValidationId.deploymentRemoval, clock.instant()))
            throw new IllegalArgumentException(ValidationId.deploymentRemoval.value() + ": " + application.get().require(instance) +
                                               " is deployed in " +
                                               deploymentsToRemove.stream()
                                                                  .map(zone -> zone.region().value())
                                                                  .collect(joining(", ")) +
                                               ", but does not include " +
                                               (deploymentsToRemove.size() > 1 ? "these zones" : "this zone") +
                                               " in deployment.xml. " +
                                               ValidationOverrides.toAllowMessage(ValidationId.deploymentRemoval));
        // Remove the instance as well, if it is no longer referenced, and contains only production deployments that are removed now.
        boolean removeInstance =    ! deploymentSpec.instanceNames().contains(instance)
                                 &&   application.get().require(instance).deployments().size() == deploymentsToRemove.size();
        for (ZoneId zone : deploymentsToRemove)
            application = deactivate(application, instance, zone);
        if (removeInstance)
            application = application.without(instance);
        return application;
    }

    /**
     * Deletes the the given application. All known instances of the applications will be deleted.
     *
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     */
    public void deleteApplication(TenantAndApplicationId id, Credentials credentials) {
        lockApplicationOrThrow(id, application -> {
            var deployments = application.get().instances().values().stream()
                                         .filter(instance -> ! instance.deployments().isEmpty())
                                         .collect(toMap(instance -> instance.name(),
                                                        instance -> instance.deployments().keySet().stream()
                                                                            .map(ZoneId::toString)
                                                                            .collect(joining(", "))));
            if ( ! deployments.isEmpty())
                throw new IllegalArgumentException("Could not delete '" + application + "': It has active deployments: " + deployments);

            for (Instance instance : application.get().instances().values()) {
                controller.routing().removeEndpointsInDns(application.get(), instance.name());
                application = application.without(instance.name());
            }

            applicationStore.removeAll(id.tenant(), id.application());
            applicationStore.removeAllTesters(id.tenant(), id.application());
            applicationStore.putMetaTombstone(id.tenant(), id.application(), clock.instant());

            accessControl.deleteApplication(id, credentials);
            curator.removeApplication(id);

            controller.jobController().collectGarbage();
            controller.notificationsDb().removeNotifications(NotificationSource.from(id));
            log.info("Deleted " + id);
        });
    }

    /**
     * Deletes the the given application instance.
     *
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     * @throws NotExistsException if the instance does not exist
     */
    public void deleteInstance(ApplicationId instanceId) {
        if (getInstance(instanceId).isEmpty())
            throw new NotExistsException("Could not delete instance '" + instanceId + "': Instance not found");

        lockApplicationOrThrow(TenantAndApplicationId.from(instanceId), application -> {
            if ( ! application.get().require(instanceId.instance()).deployments().isEmpty())
                throw new IllegalArgumentException("Could not delete '" + application + "': It has active deployments in: " +
                                                   application.get().require(instanceId.instance()).deployments().keySet().stream().map(ZoneId::toString)
                                                              .sorted().collect(joining(", ")));

            if (   ! application.get().deploymentSpec().equals(DeploymentSpec.empty)
                &&   application.get().deploymentSpec().instanceNames().contains(instanceId.instance()))
                throw new IllegalArgumentException("Can not delete '" + instanceId + "', which is specified in 'deployment.xml'; remove it there first");

            controller.routing().removeEndpointsInDns(application.get(), instanceId.instance());
            curator.writeApplication(application.without(instanceId.instance()).get());
            controller.jobController().collectGarbage();
            controller.notificationsDb().removeNotifications(NotificationSource.from(instanceId));
            log.info("Deleted " + instanceId);
        });
    }

    /**
     * Replace any previous version of this application by this instance
     *
     * @param application a locked application to store
     */
    public void store(LockedApplication application) {
        curator.writeApplication(application.get());
    }

    /**
     * Acquire a locked application to modify and store, if there is an application with the given id.
     *
     * @param applicationId ID of the application to lock and get.
     * @param action Function which acts on the locked application.
     */
    public void lockApplicationIfPresent(TenantAndApplicationId applicationId, Consumer<LockedApplication> action) {
        try (Lock lock = lock(applicationId)) {
            getApplication(applicationId).map(application -> new LockedApplication(application, lock)).ifPresent(action);
        }
    }

    /**
     * Acquire a locked application to modify and store, or throw an exception if no application has the given id.
     *
     * @param applicationId ID of the application to lock and require.
     * @param action Function which acts on the locked application.
     * @throws IllegalArgumentException when application does not exist.
     */
    public void lockApplicationOrThrow(TenantAndApplicationId applicationId, Consumer<LockedApplication> action) {
        try (Lock lock = lock(applicationId)) {
            action.accept(new LockedApplication(requireApplication(applicationId), lock));
        }
    }

    /**
     * Tells config server to schedule a restart of all nodes in this deployment
     *
     * @param restartFilter Variables to filter which nodes to restart.
     */
    public void restart(DeploymentId deploymentId, RestartFilter restartFilter) {
        configServer.restart(deploymentId, restartFilter);
    }

    /**
     * Asks the config server whether this deployment is currently healthy, i.e., serving traffic as usual.
     * If this cannot be ascertained, we must assumed it is not.
     */
    public boolean isHealthy(DeploymentId deploymentId) {
        try {
            return ! isSuspended(deploymentId); // consider adding checks again global routing status, etc.?
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed getting suspension status of " + deploymentId + ": " + Exceptions.toMessageString(e));
            return false;
        }
    }

    /**
     * Asks the config server whether this deployment is currently <i>suspended</i>:
     * Not in a state where it should receive traffic.
     */
    public boolean isSuspended(DeploymentId deploymentId) {
        return configServer.isSuspended(deploymentId);
    }

    /** Sets suspension status of the given deployment in its zone. */
    public void setSuspension(DeploymentId deploymentId, boolean suspend) {
        configServer.setSuspension(deploymentId, suspend);
    }

    /** Deactivate application in the given zone */
    public void deactivate(ApplicationId id, ZoneId zone) {
        lockApplicationOrThrow(TenantAndApplicationId.from(id),
                               application -> store(deactivate(application, id.instance(), zone)));
    }

    /**
     * Deactivates a locked application without storing it
     *
     * @return the application with the deployment in the given zone removed
     */
    private LockedApplication deactivate(LockedApplication application, InstanceName instanceName, ZoneId zone) {
        DeploymentId id = new DeploymentId(application.get().id().instance(instanceName), zone);
        try {
            configServer.deactivate(id);
        } finally {
            controller.routing().policies().refresh(application.get().id().instance(instanceName), application.get().deploymentSpec(), zone);
            if (zone.environment().isManuallyDeployed())
                applicationStore.putMetaTombstone(id, clock.instant());
            if (!zone.environment().isTest())
                controller.notificationsDb().removeNotifications(NotificationSource.from(id));
        }
        return application.with(instanceName, instance -> instance.withoutDeploymentIn(zone));
    }

    public DeploymentTrigger deploymentTrigger() { return deploymentTrigger; }

    /**
     * Returns a lock which provides exclusive rights to changing this application.
     * Any operation which stores an application need to first acquire this lock, then read, modify
     * and store the application, and finally release (close) the lock.
     */
    Lock lock(TenantAndApplicationId application) {
        return curator.lock(application);
    }

    /**
     * Returns a lock which provides exclusive rights to deploying this application to the given zone.
     */
    private Lock lockForDeployment(ApplicationId application, ZoneId zone) {
        return curator.lockForDeployment(application, zone);
    }

    /**
     * Verifies that the application can be deployed to the tenant, following these rules:
     *
     * 1. Verify that the Athenz service can be launched by the config server
     * 2. If the principal is given, verify that the principal is tenant admin or admin of the tenant domain
     * 3. If the principal is not given, verify that the Athenz domain of the tenant equals Athenz domain given in deployment.xml
     *
     * @param tenantName tenant where application should be deployed
     * @param applicationPackage application package
     * @param deployer principal initiating the deployment, possibly empty
     */
    public void verifyApplicationIdentityConfiguration(TenantName tenantName, Optional<InstanceName> instanceName, Optional<ZoneId> zoneId, ApplicationPackage applicationPackage, Optional<Principal> deployer) {
        Optional<AthenzDomain> identityDomain = applicationPackage.deploymentSpec().athenzDomain()
                                                                  .map(domain -> new AthenzDomain(domain.value()));
        if(identityDomain.isEmpty()) {
            // If there is no domain configured in deployment.xml there is nothing to do.
            return;
        }

        // Verify that the system supports launching services.
        // Consider adding a capability to the system.
        if(! (accessControl instanceof AthenzFacade)) {
            throw new IllegalArgumentException("Athenz domain and service specified in deployment.xml, but not supported by system.");
        }

        // Verify that the config server is allowed to launch the service specified
        verifyAllowedLaunchAthenzService(applicationPackage.deploymentSpec());

        // If a user principal is initiating the request, verify that the user is allowed to launch the service.
        // Either the user is member of the domain admin role, or is given the "launch" privilege on the service.
        Optional<AthenzUser> athenzUser = getUser(deployer);
        if (athenzUser.isPresent()) {
            // We only need to validate the root and instance in deployment.xml. Not possible to add dev or perf tags to deployment.xml
            var zone = zoneId.orElseThrow(() -> new IllegalArgumentException("Unable to evaluate access, no zone provided in deployment"));
            var serviceToLaunch = instanceName
                    .flatMap(instance -> applicationPackage.deploymentSpec().instance(instance))
                    .flatMap(instanceSpec -> instanceSpec.athenzService(zone.environment(), zone.region()))
                    .or(() -> applicationPackage.deploymentSpec().athenzService())
                    .map(service -> new AthenzService(identityDomain.get(), service.value()));

            if(serviceToLaunch.isPresent()) {
                if (
                        ! ((AthenzFacade) accessControl).canLaunch(athenzUser.get(), serviceToLaunch.get()) && // launch privilege
                        ! ((AthenzFacade) accessControl).hasTenantAdminAccess(athenzUser.get(), identityDomain.get()) // tenant admin
                ) {
                    throw new IllegalArgumentException("User " + athenzUser.get().getFullName() + " is not allowed to launch " +
                                                       "service " + serviceToLaunch.get().getFullName() + ". " +
                                                       "Please reach out to the domain admin.");
                }
            } else {
                // This is a rare edge case where deployment.xml specifies athenz-service on each step, but not on the root.
                // It is undefined which service should be launched, so handle this as an error.
                throw new IllegalArgumentException("Athenz domain configured, but no service defined for deployment to " + zone.value());
            }
        } else {
            // If this is a deployment pipeline, verify that the domain in deployment.xml is the same as the tenant domain. Access control is already validated before this step.
            Tenant tenant = controller.tenants().require(tenantName);
            AthenzDomain tenantDomain = ((AthenzTenant) tenant).domain();
            if ( ! Objects.equals(tenantDomain, identityDomain.get()))
                throw new IllegalArgumentException("Athenz domain in deployment.xml: [" + identityDomain.get().getName() + "] " +
                                                   "must match tenant domain: [" + tenantDomain.getName() + "]");
        }
    }

    private void rejectOldChange(Instance instance, Version platform, ApplicationVersion revision, JobId job, ZoneId zone) {
        Deployment deployment = instance.deployments().get(zone);
        if (deployment == null) return;
        if (!zone.environment().isProduction()) return;

        boolean platformIsOlder = platform.compareTo(deployment.version()) < 0 && !instance.change().isPinned();
        boolean revisionIsOlder = revision.compareTo(deployment.applicationVersion()) < 0 &&
                                  !(revision.isUnknown() && controller.system().isCd());
        if (platformIsOlder || revisionIsOlder)
            throw new IllegalArgumentException(Text.format("Rejecting deployment of application %s to %s, as the requested versions (platform: %s, application: %s)" +
                                                             " are older than the currently deployed (platform: %s, application: %s).",
                                                             job.application(), zone, platform, revision, deployment.version(), deployment.applicationVersion()));
    }

    private TenantAndApplicationId dashToUnderscore(TenantAndApplicationId id) {
        return TenantAndApplicationId.from(id.tenant().value(), id.application().value().replaceAll("-", "_"));
    }

    private ApplicationId dashToUnderscore(ApplicationId id) {
        return dashToUnderscore(TenantAndApplicationId.from(id)).instance(id.instance());
    }

    private QuotaUsage deploymentQuotaUsage(ZoneId zoneId, ApplicationId applicationId) {
        var application = configServer.nodeRepository().getApplication(zoneId, applicationId);
        return DeploymentQuotaCalculator.calculateQuotaUsage(application);
    }

    /*
     * Get the AthenzUser from this principal or Optional.empty if this does not represent a user.
     */
    private Optional<AthenzUser> getUser(Optional<Principal> deployer) {
        return deployer
                .filter(AthenzPrincipal.class::isInstance)
                .map(AthenzPrincipal.class::cast)
                .map(AthenzPrincipal::getIdentity)
                .filter(AthenzUser.class::isInstance)
                .map(AthenzUser.class::cast);
    }

    /*
     * Verifies that the configured athenz service (if any) can be launched.
     */
    private void verifyAllowedLaunchAthenzService(DeploymentSpec deploymentSpec) {
        deploymentSpec.athenzDomain().ifPresent(domain -> {
            controller.zoneRegistry().zones().reachable().ids().forEach(zone -> {
                AthenzIdentity configServerAthenzIdentity = controller.zoneRegistry().getConfigServerHttpsIdentity(zone);
                deploymentSpec.athenzService().ifPresent(service -> {
                    verifyAthenzServiceCanBeLaunchedBy(configServerAthenzIdentity, new AthenzService(domain.value(), service.value()));
                });
                deploymentSpec.instances().forEach(spec -> {
                    spec.athenzService(zone.environment(), zone.region()).ifPresent(service -> {
                        verifyAthenzServiceCanBeLaunchedBy(configServerAthenzIdentity, new AthenzService(domain.value(), service.value()));
                    });
                });
            });
        });
    }

    private void verifyAthenzServiceCanBeLaunchedBy(AthenzIdentity configServerAthenzIdentity, AthenzService athenzService) {
        if ( ! ((AthenzFacade) accessControl).canLaunch(configServerAthenzIdentity, athenzService))
            throw new IllegalArgumentException("Not allowed to launch Athenz service " + athenzService.getFullName());
    }

    /** Returns the latest known version within the given major, which is not newer than the system version. */
    public Optional<Version> lastCompatibleVersion(int targetMajorVersion) {
        VersionStatus versions = controller.readVersionStatus();
        Version systemVersion = controller.systemVersion(versions);
        return versions.versions().stream()
                       .map(VespaVersion::versionNumber)
                       .filter(version -> version.getMajor() == targetMajorVersion)
                       .filter(version -> ! version.isAfter(systemVersion))
                       .max(naturalOrder());
    }

    /** Extract deployment warnings metric from deployment result */
    private static Map<DeploymentMetrics.Warning, Integer> warningsFrom(ActivateResult result) {
        if (result.prepareResponse().log == null) return Map.of();
        Map<DeploymentMetrics.Warning, Integer> warnings = new HashMap<>();
        for (Log log : result.prepareResponse().log) {
            // TODO: Categorize warnings. Response from config server should be updated to include the appropriate
            //  category and typed log level
            if (!"warn".equalsIgnoreCase(log.level) && !"warning".equalsIgnoreCase(log.level)) continue;
            warnings.merge(DeploymentMetrics.Warning.all, 1, Integer::sum);
        }
        return Map.copyOf(warnings);
    }

}
