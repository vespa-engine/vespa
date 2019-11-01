// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NotFoundException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackageValidator;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.concurrent.Once;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.maintenance.RoutingPolicies;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.active;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.reserved;
import static java.util.Comparator.naturalOrder;

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
    private final RotationRepository rotationRepository;
    private final AccessControl accessControl;
    private final ConfigServer configServer;
    private final RoutingGenerator routingGenerator;
    private final RoutingPolicies routingPolicies;
    private final Clock clock;
    private final DeploymentTrigger deploymentTrigger;
    private final BooleanFlag provisionApplicationCertificate;
    private final ApplicationPackageValidator applicationPackageValidator;

    ApplicationController(Controller controller, CuratorDb curator,
                          AccessControl accessControl, RotationsConfig rotationsConfig,
                          Clock clock) {
        this.controller = controller;
        this.curator = curator;
        this.accessControl = accessControl;
        this.configServer = controller.serviceRegistry().configServer();
        this.routingGenerator = controller.serviceRegistry().routingGenerator();
        this.clock = clock;
        this.artifactRepository = controller.serviceRegistry().artifactRepository();
        this.applicationStore = controller.serviceRegistry().applicationStore();

        routingPolicies = new RoutingPolicies(controller);
        rotationRepository = new RotationRepository(rotationsConfig, this, curator);
        deploymentTrigger = new DeploymentTrigger(controller, controller.serviceRegistry().buildService(), clock);
        provisionApplicationCertificate = Flags.PROVISION_APPLICATION_CERTIFICATE.bindTo(controller.flagSource());
        applicationPackageValidator = new ApplicationPackageValidator(controller);

        // Update serialization format of all applications
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = clock.instant();
            int count = 0;
            for (Application application : curator.readApplications()) {
                lockApplicationIfPresent(application.id(), this::store);
                count++;
            }
            log.log(Level.INFO, String.format("Wrote %d applications in %s", count,
                                              Duration.between(start, clock.instant())));
        });

        // TODO jonmv: Do the above for applications as well when they split writes.
    }

    /** Returns the application with the given id, or null if it is not present */
    public Optional<Application> getApplication(TenantAndApplicationId id) {
        return curator.readApplication(id);
    }

    /** Returns the application with the given id, or null if it is not present */
    // TODO jonmv: remove
    public Optional<Application> getApplication(ApplicationId id) {
        return getApplication(TenantAndApplicationId.from(id));
    }

    /** Returns the instance with the given id, or null if it is not present */
    // TODO jonmv: remove or inline
    public Optional<Instance> getInstance(ApplicationId id) {
        return getApplication(id).flatMap(application -> application.get(id.instance()));
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
        return curator.readApplications();
    }

    /** Returns a snapshot of all applications of a tenant */
    public List<Application> asList(TenantName tenant) {
        return curator.readApplications(tenant);
    }

    public ArtifactRepository artifacts() { return artifactRepository; }

    public ApplicationStore applicationStore() {  return applicationStore; }

    /** Returns all content clusters in all current deployments of the given application. */
    public Map<ZoneId, List<String>> contentClustersByZone(Collection<DeploymentId> ids) {
        Map<ZoneId, List<String>> clusters = new TreeMap<>(Comparator.comparing(ZoneId::value));
        for (DeploymentId id : ids)
            clusters.put(id.zoneId(), ImmutableList.copyOf(configServer.getContentClusters(id)));
        return Collections.unmodifiableMap(clusters);
    }

    /** Returns the oldest Vespa version installed on any active or reserved production node for the given application. */
    public Version oldestInstalledPlatform(TenantAndApplicationId id) {
        return requireApplication(id).instances().values().stream()
                                     .flatMap(instance -> instance.productionDeployments().keySet().stream()
                                                                  .flatMap(zone -> configServer.nodeRepository().list(zone,
                                                                                                                      id.instance(instance.name()),
                                                                                                                      EnumSet.of(active, reserved))
                                                                                               .stream())
                                                                  .map(Node::currentVersion)
                                                                  .filter(version -> ! version.isEmpty()))
                                     .min(naturalOrder())
                                     .orElse(controller.systemVersion());
    }

    /** Change status of all global endpoints for given deployment */
    public void setGlobalRotationStatus(DeploymentId deployment, EndpointStatus status) {
        var globalEndpoints = findGlobalEndpoints(deployment);
        if (globalEndpoints.isEmpty()) throw new IllegalArgumentException(deployment + " has no global endpoints");
        globalEndpoints.forEach(endpoint -> {
            try {
                configServer.setGlobalRotationStatus(deployment, endpoint.upstreamName(), status);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set rotation status of " + endpoint + " in " + deployment, e);
            }
        });
    }

    /** Get global endpoint status for given deployment */
    public Map<RoutingEndpoint, EndpointStatus> globalRotationStatus(DeploymentId deployment) {
        var routingEndpoints = new LinkedHashMap<RoutingEndpoint, EndpointStatus>();
        findGlobalEndpoints(deployment).forEach(endpoint -> {
            var status = configServer.getGlobalRotationStatus(deployment, endpoint.upstreamName());
            routingEndpoints.put(endpoint, status);
        });
        return Collections.unmodifiableMap(routingEndpoints);
    }

    /** Find the global endpoints of given deployment */
    private List<RoutingEndpoint> findGlobalEndpoints(DeploymentId deployment) {
        return routingGenerator.endpoints(deployment).stream()
                               .filter(RoutingEndpoint::isGlobal)
                               .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Creates a new application for an existing tenant.
     *
     * @throws IllegalArgumentException if the application already exists
     */
    public Application createApplication(TenantAndApplicationId id, Optional<Credentials> credentials) {
        try (Lock lock = lock(id)) {
            if (getApplication(id).isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': Application already exists");
            if (getApplication(dashToUnderscore(id)).isPresent()) // VESPA-1945
                throw new IllegalArgumentException("Could not create '" + id + "': Application " + dashToUnderscore(id) + " already exists");

            com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId.validate(id.application().value());

            Optional<Tenant> tenant = controller.tenants().get(id.tenant());
            if (tenant.isEmpty())
                throw new IllegalArgumentException("Could not create '" + id + "': This tenant does not exist");
            if (tenant.get().type() != Tenant.Type.user) {
                if (credentials.isEmpty())
                    throw new IllegalArgumentException("Could not create '" + id + "': No credentials provided");
                accessControl.createApplication(id, credentials.get());
            }

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

    private LockedApplication withNewInstance(LockedApplication application, ApplicationId id) {
        if (id.instance().isTester())
            throw new IllegalArgumentException("'" + id + "' is a tester application!");
        InstanceId.validate(id.instance().value());

        if (getInstance(id).isPresent())
            throw new IllegalArgumentException("Could not create '" + id + "': Instance already exists");
        if (getInstance(dashToUnderscore(id)).isPresent()) // VESPA-1945
            throw new IllegalArgumentException("Could not create '" + id + "': Instance " + dashToUnderscore(id) + " already exists");

        log.info("Created " + id);
        return application.withNewInstance(id.instance());
    }

    public ActivateResult deploy(ApplicationId applicationId, ZoneId zone,
                                 Optional<ApplicationPackage> applicationPackageFromDeployer,
                                 DeployOptions options) {
        return deploy(applicationId, zone, applicationPackageFromDeployer, Optional.empty(), options);
    }

    /** Deploys an application. If the application does not exist it is created. */
    // TODO: Get rid of the options arg
    // TODO jonmv: Split this, and choose between deployDirectly and deploy in handler, excluding internally built from the latter.
    public ActivateResult deploy(ApplicationId instanceId, ZoneId zone,
                                 Optional<ApplicationPackage> applicationPackageFromDeployer,
                                 Optional<ApplicationVersion> applicationVersionFromDeployer,
                                 DeployOptions options) {
        if (instanceId.instance().isTester())
            throw new IllegalArgumentException("'" + instanceId + "' is a tester application!");

        TenantAndApplicationId applicationId = TenantAndApplicationId.from(instanceId);
        if (   getApplication(applicationId).isEmpty()
            && controller.tenants().require(instanceId.tenant()).type() == Tenant.Type.user)
            createApplication(applicationId, Optional.empty());

        if (getInstance(instanceId).isEmpty())
            createInstance(instanceId);

        try (Lock deploymentLock = lockForDeployment(instanceId, zone)) {
            Version platformVersion;
            ApplicationVersion applicationVersion;
            ApplicationPackage applicationPackage;
            Set<ContainerEndpoint> endpoints;
            Optional<ApplicationCertificate> applicationCertificate;

            try (Lock lock = lock(applicationId)) {
                LockedApplication application = new LockedApplication(requireApplication(applicationId), lock);
                InstanceName instance = instanceId.instance();

                boolean manuallyDeployed = options.deployDirectly || zone.environment().isManuallyDeployed();
                boolean preferOldestVersion = options.deployCurrentVersion;

                // Determine versions to use.
                if (manuallyDeployed) {
                    applicationVersion = applicationVersionFromDeployer.orElse(ApplicationVersion.unknown);
                    applicationPackage = applicationPackageFromDeployer.orElseThrow(
                            () -> new IllegalArgumentException("Application package must be given when deploying to " + zone));
                    platformVersion = options.vespaVersion.map(Version::new)
                                                          .orElse(applicationPackage.deploymentSpec().majorVersion()
                                                                                    .flatMap(this::lastCompatibleVersion)
                                                                                    .orElseGet(controller::systemVersion));
                }
                else {
                    JobType jobType = JobType.from(controller.system(), zone)
                                             .orElseThrow(() -> new IllegalArgumentException("No job is known for " + zone + "."));
                    Optional<JobStatus> job = Optional.ofNullable(application.get().require(instance).deploymentJobs().jobStatus().get(jobType));
                    if (   job.isEmpty()
                        || job.get().lastTriggered().isEmpty()
                        || job.get().lastCompleted().isPresent() && job.get().lastCompleted().get().at().isAfter(job.get().lastTriggered().get().at()))
                        return unexpectedDeployment(instanceId, zone);
                    JobRun triggered = job.get().lastTriggered().get();
                    platformVersion = preferOldestVersion ? triggered.sourcePlatform().orElse(triggered.platform())
                                                          : triggered.platform();
                    applicationVersion = preferOldestVersion ? triggered.sourceApplication().orElse(triggered.application())
                                                             : triggered.application();

                    applicationPackage = getApplicationPackage(instanceId, application.get().internal(), applicationVersion);
                    applicationPackage = withTesterCertificate(applicationPackage, instanceId, jobType);
                    validateRun(application.get(), instance, zone, platformVersion, applicationVersion);
                }

                if (controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) {
                    // Provisions a new certificate if missing
                    applicationCertificate = getApplicationCertificate(application.get().require(instance));
                } else {
                    applicationCertificate = Optional.empty();
                }

                // TODO jonmv: REMOVE! This is now irrelevant for non-CD-test deployments and non-unit tests.
                if (   ! preferOldestVersion
                    && ! application.get().internal()
                    && ! zone.environment().isManuallyDeployed()) {
                    application = storeWithUpdatedConfig(application, applicationPackage);
                }

                endpoints = registerEndpointsInDns(applicationPackage.deploymentSpec(), application.get().require(instanceId.instance()), zone);
            } // Release application lock while doing the deployment, which is a lengthy task.

            // Carry out deployment without holding the application lock.
            options = withVersion(platformVersion, options);
            ActivateResult result = deploy(instanceId, applicationPackage, zone, options, endpoints,
                                           applicationCertificate.orElse(null));

            lockApplicationOrThrow(applicationId, application ->
                    store(application.with(instanceId.instance(),
                                           instance -> instance.withNewDeployment(zone, applicationVersion, platformVersion,
                                                                                  clock.instant(), warningsFrom(result)))));
            return result;
        }
    }

    private ApplicationPackage withTesterCertificate(ApplicationPackage applicationPackage, ApplicationId id, JobType type) {
        if (applicationPackage.trustedCertificates().isEmpty())
            return applicationPackage;

        // TODO jonmv: move this to the caller, when external build service is removed.
        Run run = controller.jobController().last(id, type)
                            .orElseThrow(() -> new IllegalStateException("Last run of " + type + " for " + id + " not found"));
        if (run.testerCertificate().isEmpty())
            return applicationPackage;

        return applicationPackage.withTrustedCertificate(run.testerCertificate().get());
    }

    /** Fetches the requested application package from the artifact store(s). */
    public ApplicationPackage getApplicationPackage(ApplicationId id, boolean internal, ApplicationVersion version) {
        try {
            return internal
                    ? new ApplicationPackage(applicationStore.get(id.tenant(), id.application(), version))
                    : new ApplicationPackage(artifactRepository.getApplicationPackage(id, version.id()));
        }
        catch (RuntimeException e) { // If application has switched deployment pipeline, artifacts stored prior to the switch are in the other artifact store.
            try {
                log.info("Fetching application package for " + id + " from alternate repository; it is now deployed "
                         + (internal ? "internally" : "externally") + "\nException was: " + Exceptions.toMessageString(e));
                return internal
                        ? new ApplicationPackage(artifactRepository.getApplicationPackage(id, version.id()))
                        : new ApplicationPackage(applicationStore.get(id.tenant(), id.application(), version));
            }
            catch (RuntimeException s) { // If this fails, too, the first failure is most likely the relevant one.
                e.addSuppressed(s);
                throw e;
            }
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
            // Clean up deployment jobs that are no longer referenced by deployment spec
            if (application.get().instances().containsKey(name)) {
                DeploymentSpec deploymentSpec = application.get().deploymentSpec();
                application = application.with(name, instance -> withoutUnreferencedDeploymentJobs(deploymentSpec, instance));
            }
        }

        for (InstanceName instance : declaredInstances)
            if (applicationPackage.deploymentSpec().requireInstance(instance).deploysTo(Environment.prod))
                application = withRotation(applicationPackage.deploymentSpec(), application, instance);

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
            DeployOptions options = withVersion(version, DeployOptions.none());
            return deploy(application.id(), applicationPackage, zone, options, Set.of(), /* No application cert */ null);
        } else {
           throw new RuntimeException("This system application does not have an application package: " + application.id().toShortString());
        }
    }

    /** Deploys the given tester application to the given zone. */
    public ActivateResult deployTester(TesterId tester, ApplicationPackage applicationPackage, ZoneId zone, DeployOptions options) {
        return deploy(tester.id(), applicationPackage, zone, options, Set.of(), /* No application cert for tester*/ null);
    }

    private ActivateResult deploy(ApplicationId application, ApplicationPackage applicationPackage,
                                  ZoneId zone, DeployOptions deployOptions, Set<ContainerEndpoint> endpoints,
                                  ApplicationCertificate applicationCertificate) {
        DeploymentId deploymentId = new DeploymentId(application, zone);
        try {
            ConfigServer.PreparedApplication preparedApplication =
                    configServer.deploy(deploymentId, deployOptions, endpoints, applicationCertificate, applicationPackage.zippedContent());
            return new ActivateResult(new RevisionId(applicationPackage.hash()), preparedApplication.prepareResponse(),
                                      applicationPackage.zippedContent().length);
        } finally {
            // Even if prepare fails, a load balancer may have been provisioned. Always refresh routing policies so that
            // any DNS updates can be propagated as early as possible.
            routingPolicies.refresh(application, applicationPackage.deploymentSpec(), zone);
        }
    }

    /** Makes sure the application has a global rotation, if eligible. */
    private LockedApplication withRotation(DeploymentSpec deploymentSpec, LockedApplication application, InstanceName instanceName) {
        try (RotationLock rotationLock = rotationRepository.lock()) {
            var rotations = rotationRepository.getOrAssignRotations(deploymentSpec,
                                                                    application.get().require(instanceName),
                                                                    rotationLock);
            application = application.with(instanceName, instance -> instance.with(rotations));
            store(application); // store assigned rotation even if deployment fails
        }
        return application;
    }

    /**
     * Register endpoints for rotations assigned to given application and zone in DNS.
     *
     * @return the registered endpoints
     */
    private Set<ContainerEndpoint> registerEndpointsInDns(DeploymentSpec deploymentSpec, Instance instance, ZoneId zone) {
        var containerEndpoints = new HashSet<ContainerEndpoint>();
        boolean registerLegacyNames = deploymentSpec.instance(instance.name())
                                                    .flatMap(DeploymentInstanceSpec::globalServiceId)
                                                    .isPresent();
        for (var assignedRotation : instance.rotations()) {
            var names = new ArrayList<String>();
            var endpoints = instance.endpointsIn(controller.system(), assignedRotation.endpointId())
                                    .scope(Endpoint.Scope.global);

            // Skip rotations which do not apply to this zone. Legacy names always point to all zones
            if (!registerLegacyNames && !assignedRotation.regions().contains(zone.region())) {
                continue;
            }

            // Omit legacy DNS names when assigning rotations using <endpoints/> syntax
            if (!registerLegacyNames) {
                endpoints = endpoints.legacy(false);
            }

            // Register names in DNS
            var rotation = rotationRepository.getRotation(assignedRotation.rotationId());
            if (rotation.isPresent()) {
                endpoints.asList().forEach(endpoint -> {
                    controller.nameServiceForwarder().createCname(RecordName.from(endpoint.dnsName()),
                                                                  RecordData.fqdn(rotation.get().name()),
                                                                  Priority.normal);
                    names.add(endpoint.dnsName());
                });
            }

            // Include rotation ID as a valid name of this container endpoint (required by global routing health checks)
            names.add(assignedRotation.rotationId().asString());
            containerEndpoints.add(new ContainerEndpoint(assignedRotation.clusterId().value(), names));
        }
        return Collections.unmodifiableSet(containerEndpoints);
    }

    private Optional<ApplicationCertificate> getApplicationCertificate(Instance instance) {
        boolean provisionCertificate = provisionApplicationCertificate.with(FetchVector.Dimension.APPLICATION_ID,
                                                                            instance.id().serializedForm()).value();
        if (!provisionCertificate) {
            return Optional.empty();
        }

        // Re-use certificate if already provisioned
        Optional<ApplicationCertificate> applicationCertificate = curator.readApplicationCertificate(instance.id());
        if(applicationCertificate.isPresent())
            return applicationCertificate;

        ApplicationCertificate newCertificate = controller.serviceRegistry().applicationCertificateProvider().requestCaSignedCertificate(instance.id(), dnsNamesOf(instance.id()));
        curator.writeApplicationCertificate(instance.id(), newCertificate);

        return Optional.of(newCertificate);
    }

    /** Returns all valid DNS names of given application */
    private List<String> dnsNamesOf(ApplicationId applicationId) {
        List<String> endpointDnsNames = new ArrayList<>();

        // We add first an endpoint name based on a hash of the applicationId,
        // as the certificate provider requires the first CN to be < 64 characters long.
        endpointDnsNames.add(Endpoint.createHashedCn(applicationId, controller.system()));

        var globalDefaultEndpoint = Endpoint.of(applicationId).named(EndpointId.defaultId());
        var rotationEndpoints = Endpoint.of(applicationId).wildcard();

        var zoneLocalEndpoints = controller.zoneRegistry().zones().directlyRouted().zones().stream().flatMap(zone -> Stream.of(
                Endpoint.of(applicationId).target(ClusterSpec.Id.from("default"), zone.getId()),
                Endpoint.of(applicationId).wildcard(zone.getId())
        ));

        Stream.concat(Stream.of(globalDefaultEndpoint, rotationEndpoints), zoneLocalEndpoints)
              .map(Endpoint.EndpointBuilder::directRouting)
              .map(endpoint -> endpoint.on(Endpoint.Port.tls()))
              .map(endpointBuilder -> endpointBuilder.in(controller.system()))
              .map(Endpoint::dnsName).forEach(endpointDnsNames::add);

        return Collections.unmodifiableList(endpointDnsNames);
    }

    private ActivateResult unexpectedDeployment(ApplicationId application, ZoneId zone) {
        Log logEntry = new Log();
        logEntry.level = "WARNING";
        logEntry.time = clock.instant().toEpochMilli();
        logEntry.message = "Ignoring deployment of application '" + application + "' to " + zone +
                           " as a deployment is not currently expected";
        PrepareResponse prepareResponse = new PrepareResponse();
        prepareResponse.log = List.of(logEntry);
        prepareResponse.configChangeActions = new ConfigChangeActions(List.of(), List.of());
        return new ActivateResult(new RevisionId("0"), prepareResponse, 0);
    }

    private LockedApplication withoutDeletedDeployments(LockedApplication application, InstanceName instance) {
        DeploymentSpec deploymentSpec = application.get().deploymentSpec();
        List<ZoneId> deploymentsToRemove = application.get().require(instance).productionDeployments().values().stream()
                                                      .map(Deployment::zone)
                                                      .filter(zone ->      deploymentSpec.instance(instance).isEmpty()
                                                                      || ! deploymentSpec.requireInstance(instance).includes(zone.environment(),
                                                                                                                             Optional.of(zone.region())))
                                                      .collect(Collectors.toList());

        if (deploymentsToRemove.isEmpty())
            return application;

        if ( ! application.get().validationOverrides().allows(ValidationId.deploymentRemoval, clock.instant()))
            throw new IllegalArgumentException(ValidationId.deploymentRemoval.value() + ": " + application.get().require(instance) +
                                               " is deployed in " +
                                               deploymentsToRemove.stream()
                                                                  .map(zone -> zone.region().value())
                                                                  .collect(Collectors.joining(", ")) +
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

    private Instance withoutUnreferencedDeploymentJobs(DeploymentSpec deploymentSpec, Instance instance) {
        for (JobType job : JobList.from(instance).production().mapToList(JobStatus::type)) {
            ZoneId zone = job.zone(controller.system());
            if (deploymentSpec.instance(instance.name())
                              .map(spec -> spec.includes(zone.environment(), Optional.of(zone.region())))
                              .orElse(false))
                continue;
            instance = instance.withoutDeploymentJob(job);
        }
        return instance;
    }

    private DeployOptions withVersion(Version version, DeployOptions options) {
        return new DeployOptions(options.deployDirectly,
                                 Optional.of(version),
                                 options.ignoreValidationErrors,
                                 options.deployCurrentVersion);
    }

    /** Returns the endpoints of the deployment, or empty if the request fails */
    public List<URI> getDeploymentEndpoints(DeploymentId deploymentId) {
        if ( ! getInstance(deploymentId.applicationId())
                .map(application -> application.deployments().containsKey(deploymentId.zoneId()))
                .orElse(deploymentId.applicationId().instance().isTester()))
            throw new NotExistsException("Deployment", deploymentId.toString());

        try {
            return ImmutableList.copyOf(routingGenerator.endpoints(deploymentId).stream()
                                                        .map(RoutingEndpoint::endpoint)
                                                        .map(URI::create)
                                                        .iterator());
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to get endpoint information for " + deploymentId, e);
            return Collections.emptyList();
        }
    }

    /** Returns the non-empty endpoints per cluster in the given deployment, or empty if endpoints can't be found. */
    public Map<ClusterSpec.Id, URI> clusterEndpoints(DeploymentId id) {
        if ( ! getInstance(id.applicationId())
                .map(application -> application.deployments().containsKey(id.zoneId()))
                .orElse(id.applicationId().instance().isTester()))
            throw new NotExistsException("Deployment", id.toString());

        // TODO(jvenstad): Swap to use routingPolicies first, when this is ready.
        try {
            var endpoints = routingGenerator.clusterEndpoints(id);
            if ( ! endpoints.isEmpty())
                return endpoints;
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to get endpoint information for " + id, e);
        }
        return routingPolicies.get(id).stream()
                              .filter(policy -> policy.endpointIn(controller.system()).scope() == Endpoint.Scope.zone)
                              .collect(Collectors.toUnmodifiableMap(policy -> policy.cluster(),
                                                                    policy -> policy.endpointIn(controller.system()).url()));
    }

    /** Returns all zone-specific cluster endpoints for the given application, in the given zones. */
    public Map<ZoneId, Map<ClusterSpec.Id, URI>> clusterEndpoints(Collection<DeploymentId> ids) {
        Map<ZoneId, Map<ClusterSpec.Id, URI>> deployments = new TreeMap<>(Comparator.comparing(ZoneId::value));
        for (DeploymentId id : ids) {
            var endpoints = clusterEndpoints(id);
            if ( ! endpoints.isEmpty()) {
                deployments.put(id.zoneId(), endpoints);
            }
        }
        return Collections.unmodifiableMap(deployments);
    }

    /**
     * Deletes the the given application. All known instances of the applications will be deleted.
     *
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     */
    public void deleteApplication(TenantAndApplicationId id, Optional<Credentials> credentials) {
        Tenant tenant = controller.tenants().require(id.tenant());
        if (tenant.type() != Tenant.Type.user && credentials.isEmpty())
            throw new IllegalArgumentException("Could not delete application '" + id + "': No credentials provided");

        // Find all instances of the application
        List<ApplicationId> instances = requireApplication(id).instances().keySet().stream()
                                                              .map(id::instance)
                                                              .collect(Collectors.toUnmodifiableList());
        if (instances.size() > 1)
            throw new IllegalArgumentException("Could not delete application; more than one instance present: " + instances);

        for (ApplicationId instance : instances)
            deleteInstance(instance);

        applicationStore.removeAll(id.tenant(), id.application());
        applicationStore.removeAllTesters(id.tenant(), id.application());

        if (tenant.type() != Tenant.Type.user)
            accessControl.deleteApplication(id, credentials.get());
        curator.removeApplication(id);
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
                                                              .sorted().collect(Collectors.joining(", ")));

            Instance instance = application.get().require(instanceId.instance());
            instance.rotations().forEach(assignedRotation -> {
                var endpoints = instance.endpointsIn(controller.system(), assignedRotation.endpointId());
                endpoints.asList().stream()
                         .map(Endpoint::dnsName)
                         .forEach(name -> {
                             controller.nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordName.from(name), Priority.normal);
                         });
            });
            curator.writeApplication(application.without(instanceId.instance()).get());

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
     * @param hostname If non-empty, restart will only be scheduled for this host
     */
    public void restart(DeploymentId deploymentId, Optional<Hostname> hostname) {
        configServer.restart(deploymentId, hostname);
    }

    /**
     * Asks the config server whether this deployment is currently <i>suspended</i>:
     * Not in a state where it should receive traffic.
     */
    public boolean isSuspended(DeploymentId deploymentId) {
        try {
            return configServer.isSuspended(deploymentId);
        }
        catch (ConfigServerException e) {
            if (e.getErrorCode() == ConfigServerException.ErrorCode.NOT_FOUND)
                return false;
            throw e;
        }
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
        try {
            configServer.deactivate(new DeploymentId(application.get().id().instance(instanceName), zone));
        } catch (NotFoundException ignored) {
            // ok; already gone
        } finally {
            routingPolicies.refresh(application.get().id().instance(instanceName), application.get().deploymentSpec(), zone);
        }
        return application.with(instanceName, instance -> instance.withoutDeploymentIn(zone));
    }

    public DeploymentTrigger deploymentTrigger() { return deploymentTrigger; }

    private TenantAndApplicationId dashToUnderscore(TenantAndApplicationId id) {
        return TenantAndApplicationId.from(id.tenant().value(), id.application().value().replaceAll("-", "_"));
    }

    private ApplicationId dashToUnderscore(ApplicationId id) {
        return dashToUnderscore(TenantAndApplicationId.from(id)).instance(id.instance());
    }

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

    /** Verify that we don't downgrade an existing production deployment. */
    private void validateRun(Application application, InstanceName instance, ZoneId zone, Version platformVersion, ApplicationVersion applicationVersion) {
        Deployment deployment = application.require(instance).deployments().get(zone);
        if (   zone.environment().isProduction() && deployment != null
            && (   platformVersion.compareTo(deployment.version()) < 0 && ! application.change().isPinned()
                || applicationVersion.compareTo(deployment.applicationVersion()) < 0))
            throw new IllegalArgumentException(String.format("Rejecting deployment of application %s to %s, as the requested versions (platform: %s, application: %s)" +
                                                             " are older than the currently deployed (platform: %s, application: %s).",
                                                             application.id().instance(instance), zone, platformVersion, applicationVersion, deployment.version(), deployment.applicationVersion()));
    }

    /** Returns the rotation repository, used for managing global rotation assignments */
    public RotationRepository rotationRepository() {
        return rotationRepository;
    }

    public RoutingPolicies routingPolicies() {
        return routingPolicies;
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
    public void verifyApplicationIdentityConfiguration(TenantName tenantName, ApplicationPackage applicationPackage, Optional<Principal> deployer) {
        verifyAllowedLaunchAthenzService(applicationPackage.deploymentSpec());

        applicationPackage.deploymentSpec().athenzDomain().ifPresent(identityDomain -> {
            Tenant tenant = controller.tenants().require(tenantName);
            deployer.filter(AthenzPrincipal.class::isInstance)
                    .map(AthenzPrincipal.class::cast)
                    .map(AthenzPrincipal::getIdentity)
                    .filter(AthenzUser.class::isInstance)
                    .ifPresentOrElse(user -> {
                                         if ( ! ((AthenzFacade) accessControl).hasTenantAdminAccess(user, new AthenzDomain(identityDomain.value())))
                                             throw new IllegalArgumentException("User " + user.getFullName() + " is not allowed to launch " +
                                                                                "services in Athenz domain " + identityDomain.value() + ". " +
                                                                                "Please reach out to the domain admin.");
                                     },
                                     () -> {
                                         if (tenant.type() != Tenant.Type.athenz)
                                             throw new IllegalArgumentException("Athenz domain defined in deployment.xml, but no " +
                                                                                "Athenz domain for tenant " + tenantName.value());

                                         AthenzDomain tenantDomain = ((AthenzTenant) tenant).domain();
                                         if ( ! Objects.equals(tenantDomain.getName(), identityDomain.value()))
                                             throw new IllegalArgumentException("Athenz domain in deployment.xml: [" + identityDomain.value() + "] " +
                                                                                "must match tenant domain: [" + tenantDomain.getName() + "]");
                                     });
        });
    }

    /*
     * Verifies that the configured athenz service (if any) can be launched.
     */
    private void verifyAllowedLaunchAthenzService(DeploymentSpec deploymentSpec) {
        deploymentSpec.athenzDomain().ifPresent(athenzDomain -> {
            controller.zoneRegistry().zones().reachable().ids()
                      .forEach(zone -> {
                          AthenzIdentity configServerAthenzIdentity = controller.zoneRegistry().getConfigServerHttpsIdentity(zone);
                          deploymentSpec.athenzService(zone.environment(), zone.region())
                                        .map(service -> new AthenzService(athenzDomain.value(), service.value()))
                                        .ifPresent(service -> {
                                            boolean allowedToLaunch = ((AthenzFacade) accessControl).canLaunch(configServerAthenzIdentity, service);
                                            if (!allowedToLaunch)
                                                throw new IllegalArgumentException("Not allowed to launch Athenz service " + service.getFullName());
                                        });
                      });
        });
    }

    /** Returns the latest known version within the given major. */
    private Optional<Version> lastCompatibleVersion(int targetMajorVersion) {
        return controller.versionStatus().versions().stream()
                         .map(VespaVersion::versionNumber)
                         .filter(version -> version.getMajor() == targetMajorVersion)
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
