// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneApi;
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
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.DeploymentSpecValidator;
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
    private final DeploymentSpecValidator deploymentSpecValidator;

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
        deploymentSpecValidator = new DeploymentSpecValidator(controller);

        // Update serialization format of all applications
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = clock.instant();
            int count = 0;
            for (Instance instance : curator.readInstances()) {
                lockIfPresent(instance.id(), this::store);
                count++;
            }
            log.log(Level.INFO, String.format("Wrote %d applications in %s", count,
                                              Duration.between(start, clock.instant())));
        });

        // TODO jonmv: Do the above for applications as well when they split writes.
    }

    /** Returns the application with the given id, or null if it is not present */
    public Optional<Application> getApplication(ApplicationId id) {
        return curator.readApplication(id);
    }

    /** Returns the application with the given id, or null if it is not present */
    public Optional<Instance> get(ApplicationId id) {
        return curator.readInstance(id);
    }

    /**
     * Returns the application with the given id
     *
     * @throws IllegalArgumentException if it does not exist
     */
    public Application requireApplication(ApplicationId id) {
        return getApplication(id).orElseThrow(() -> new IllegalArgumentException(id + " not found"));
    }

    /**
     * Returns the instance with the given id
     *
     * @throws IllegalArgumentException if it does not exist
     */
    public Instance require(ApplicationId id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException(id + " not found"));
    }

    /** Returns a snapshot of all applications */
    public List<Application> applicationList() {
        return curator.readApplications();
    }

    /** Returns all applications of a tenant */
    public List<Application> applicationList(TenantName tenant) {
        return curator.readApplications(tenant);
    }

    /** Returns a snapshot of all instances */
    public List<Instance> asList() {
        return curator.readInstances();
    }

    /** Returns all instances of a tenant */
    public List<Instance> asList(TenantName tenant) {
        return curator.readInstances(tenant);
    }

    /** Returns all instances of an application */
    public List<Instance> asList(TenantAndApplicationId id) {
        return curator.readInstances(id);
    }

    public ArtifactRepository artifacts() { return artifactRepository; }

    public ApplicationStore applicationStore() {  return applicationStore; }

    /** Returns the oldest Vespa version installed on any active or reserved production node for the given application. */
    public Version oldestInstalledPlatform(ApplicationId id) {
        return asList(TenantAndApplicationId.from(id)).stream()
                                                      .flatMap(instance -> instance.productionDeployments().keySet().stream()
                                                                                   .flatMap(zone -> configServer.nodeRepository().list(zone, id, EnumSet.of(active, reserved)).stream())
                                                                                   .map(Node::currentVersion)
                                                                                   .filter(version -> ! version.isEmpty()))
                                                      .min(naturalOrder())
                                                      .orElse(controller.systemVersion());
    }

    /** Change the global endpoint status for given deployment */
    public void setGlobalRotationStatus(DeploymentId deployment, EndpointStatus status) {
        findGlobalEndpoint(deployment).map(endpoint -> {
            try {
                configServer.setGlobalRotationStatus(deployment, endpoint.upstreamName(), status);
                return endpoint;
            } catch (Exception e) {
                throw new RuntimeException("Failed to set rotation status of " + deployment, e);
            }
        }).orElseThrow(() -> new IllegalArgumentException("No global endpoint exists for " + deployment));
    }

    /** Get global endpoint status for given deployment */
    public Map<RoutingEndpoint, EndpointStatus> globalRotationStatus(DeploymentId deployment) {
        return findGlobalEndpoint(deployment).map(endpoint -> {
            try {
                EndpointStatus status = configServer.getGlobalRotationStatus(deployment, endpoint.upstreamName());
                return Map.of(endpoint, status);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get rotation status of " + deployment, e);
            }
        }).orElseGet(Collections::emptyMap);
    }

    /** Find the global endpoint of given deployment, if any */
    private Optional<RoutingEndpoint> findGlobalEndpoint(DeploymentId deployment) {
        return routingGenerator.endpoints(deployment).stream()
                               .filter(RoutingEndpoint::isGlobal)
                               .findFirst();
    }

    /**
     * Creates a new application for an existing tenant.
     *
     * @throws IllegalArgumentException if the application already exists
     */
    public Application createApplication(ApplicationId id, Optional<Credentials> credentials) {
        if (id.instance().isTester())
            throw new IllegalArgumentException("'" + id + "' is a tester application!");
        try (Lock lock = lock(id)) {
            // Validate only application names which do not already exist.
            if (asList(id.tenant()).stream().noneMatch(application -> application.id().application().equals(id.application())))
                com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId.validate(id.application().value());

            Optional<Tenant> tenant = controller.tenants().get(id.tenant());
            if (tenant.isEmpty())
                throw new IllegalArgumentException("Could not create '" + id + "': This tenant does not exist");
            if (get(id).isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': Application already exists");
            if (get(dashToUnderscore(id)).isPresent()) // VESPA-1945
                throw new IllegalArgumentException("Could not create '" + id + "': Application " + dashToUnderscore(id) + " already exists");
            if (tenant.get().type() != Tenant.Type.user) {
                if (credentials.isEmpty())
                    throw new IllegalArgumentException("Could not create '" + id + "': No credentials provided");

                if ( ! id.instance().isTester()) // Only store the application permits for non-user applications.
                    accessControl.createApplication(id, credentials.get());
            }
            LockedApplication application = new LockedApplication(new Application(id, clock.instant()), lock);
            store(application);
            log.info("Created " + application);
            return application.get();
        }
    }

    public ActivateResult deploy(ApplicationId applicationId, ZoneId zone,
                                 Optional<ApplicationPackage> applicationPackageFromDeployer,
                                 DeployOptions options) {
        return deploy(applicationId, zone, applicationPackageFromDeployer, Optional.empty(), options, Optional.empty());
    }

    /** Deploys an application. If the application does not exist it is created. */
    // TODO: Get rid of the options arg
    // TODO(jvenstad): Split this, and choose between deployDirectly and deploy in handler, excluding internally built from the latter.
    public ActivateResult deploy(ApplicationId applicationId, ZoneId zone,
                                 Optional<ApplicationPackage> applicationPackageFromDeployer,
                                 Optional<ApplicationVersion> applicationVersionFromDeployer,
                                 DeployOptions options,
                                 Optional<Principal> deployingIdentity) {
        if (applicationId.instance().isTester())
            throw new IllegalArgumentException("'" + applicationId + "' is a tester application!");

        Tenant tenant = controller.tenants().require(applicationId.tenant());
        if (tenant.type() == Tenant.Type.user && get(applicationId).isEmpty())
            createApplication(applicationId, Optional.empty());

        try (Lock deploymentLock = lockForDeployment(applicationId, zone)) {
            Version platformVersion;
            ApplicationVersion applicationVersion;
            ApplicationPackage applicationPackage;
            Set<ContainerEndpoint> endpoints;
            Optional<ApplicationCertificate> applicationCertificate;

            try (Lock lock = lock(applicationId)) {
                LockedInstance application = new LockedInstance(require(applicationId), lock);

                boolean manuallyDeployed = options.deployDirectly || zone.environment().isManuallyDeployed();
                boolean preferOldestVersion = options.deployCurrentVersion;

                // Determine versions to use.
                if (manuallyDeployed) {
                    applicationVersion = applicationVersionFromDeployer.orElse(ApplicationVersion.unknown);
                    applicationPackage = applicationPackageFromDeployer.orElseThrow(
                            () -> new IllegalArgumentException("Application package must be given when deploying to " + zone));
                    platformVersion = options.vespaVersion.map(Version::new).orElse(applicationPackage.deploymentSpec().majorVersion()
                                                                                                      .flatMap(this::lastCompatibleVersion)
                                                                                                      .orElseGet(controller::systemVersion));
                }
                else {
                    JobType jobType = JobType.from(controller.system(), zone)
                                             .orElseThrow(() -> new IllegalArgumentException("No job is known for " + zone + "."));
                    Optional<JobStatus> job = Optional.ofNullable(application.get().deploymentJobs().jobStatus().get(jobType));
                    if (   job.isEmpty()
                        || job.get().lastTriggered().isEmpty()
                        || job.get().lastCompleted().isPresent() && job.get().lastCompleted().get().at().isAfter(job.get().lastTriggered().get().at()))
                        return unexpectedDeployment(applicationId, zone);
                    JobRun triggered = job.get().lastTriggered().get();
                    platformVersion = preferOldestVersion ? triggered.sourcePlatform().orElse(triggered.platform())
                                                          : triggered.platform();
                    applicationVersion = preferOldestVersion ? triggered.sourceApplication().orElse(triggered.application())
                                                             : triggered.application();

                    applicationPackage = getApplicationPackage(application.get(), applicationVersion);
                    applicationPackage = withTesterCertificate(applicationPackage, applicationId, jobType);
                    validateRun(application.get(), zone, platformVersion, applicationVersion);
                }

                // TODO jonmv: Remove this when all packages are validated upon submission, as in ApplicationApiHandler.submit(...).
                verifyApplicationIdentityConfiguration(applicationId.tenant(), applicationPackage, deployingIdentity);

                // Assign and register endpoints
                application = withRotation(application, zone);
                endpoints = registerEndpointsInDns(application.get(), zone);

                if (controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) {
                    // Get application certificate (provisions a new certificate if missing)
                    List<? extends ZoneApi> zones = controller.zoneRegistry().zones().all().zones();
                    applicationCertificate = getApplicationCertificate(application.get());
                } else {
                    applicationCertificate = Optional.empty();
                }

                // Update application with information from application package
                if (   ! preferOldestVersion
                    && ! application.get().deploymentJobs().deployedInternally()
                    && ! zone.environment().isManuallyDeployed())
                    // TODO(jvenstad): Store only on submissions
                    storeWithUpdatedConfig(application, applicationPackage);
            } // Release application lock while doing the deployment, which is a lengthy task.

            // Carry out deployment without holding the application lock.
            options = withVersion(platformVersion, options);
            ActivateResult result = deploy(applicationId, applicationPackage, zone, options, endpoints,
                                           applicationCertificate.orElse(null));

            lockOrThrow(applicationId, application ->
                    store(application.withNewDeployment(zone, applicationVersion, platformVersion, clock.instant(),
                                                        warningsFrom(result))));
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
    public ApplicationPackage getApplicationPackage(Instance instance, ApplicationVersion version) {
        try {
            return instance.deploymentJobs().deployedInternally()
                    ? new ApplicationPackage(applicationStore.get(instance.id(), version))
                    : new ApplicationPackage(artifactRepository.getApplicationPackage(instance.id(), version.id()));
        }
        catch (RuntimeException e) { // If application has switched deployment pipeline, artifacts stored prior to the switch are in the other artifact store.
            try {
                log.info("Fetching application package for " + instance.id() + " from alternate repository; it is now deployed "
                         + (instance.deploymentJobs().deployedInternally() ? "internally" : "externally") + "\nException was: " + Exceptions.toMessageString(e));
                return instance.deploymentJobs().deployedInternally()
                        ? new ApplicationPackage(artifactRepository.getApplicationPackage(instance.id(), version.id()))
                        : new ApplicationPackage(applicationStore.get(instance.id(), version));
            }
            catch (RuntimeException s) { // If this fails, too, the first failure is most likely the relevant one.
                e.addSuppressed(s);
                throw e;
            }
        }
    }

    /** Stores the deployment spec and validation overrides from the application package, and runs cleanup. */
    public LockedInstance storeWithUpdatedConfig(LockedInstance application, ApplicationPackage applicationPackage) {
        deploymentSpecValidator.validate(applicationPackage.deploymentSpec());

        application = application.with(applicationPackage.deploymentSpec());
        application = application.with(applicationPackage.validationOverrides());

        // Delete zones not listed in DeploymentSpec, if allowed
        // We do this at deployment time for externally built applications, and at submission time
        // for internally built ones, to be able to return a validation failure message when necessary
        application = withoutDeletedDeployments(application);

        // Clean up deployment jobs that are no longer referenced by deployment spec
        application = withoutUnreferencedDeploymentJobs(application);

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
                    configServer.deploy(deploymentId, deployOptions, Set.of(), endpoints, applicationCertificate, applicationPackage.zippedContent());
            return new ActivateResult(new RevisionId(applicationPackage.hash()), preparedApplication.prepareResponse(),
                                      applicationPackage.zippedContent().length);
        } finally {
            // Even if prepare fails, a load balancer may have been provisioned. Always refresh routing policies so that
            // any DNS updates can be propagated as early as possible.
            routingPolicies.refresh(application, applicationPackage.deploymentSpec(), zone);
        }
    }

    /** Makes sure the application has a global rotation, if eligible. */
    private LockedInstance withRotation(LockedInstance application, ZoneId zone) {
        if (zone.environment() == Environment.prod) {
            try (RotationLock rotationLock = rotationRepository.lock()) {
                var rotations = rotationRepository.getOrAssignRotations(application.get(), rotationLock);
                application = application.with(rotations);
                store(application); // store assigned rotation even if deployment fails
            }
        }
        return application;
    }

    /**
     * Register endpoints for rotations assigned to given application and zone in DNS.
     *
     * @return the registered endpoints
     */
    private Set<ContainerEndpoint> registerEndpointsInDns(Instance instance, ZoneId zone) {
        var containerEndpoints = new HashSet<ContainerEndpoint>();
        var registerLegacyNames = instance.deploymentSpec().globalServiceId().isPresent();
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

        var globalDefaultEndpoint = Endpoint.of(applicationId).named(EndpointId.default_());
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

    private LockedInstance withoutDeletedDeployments(LockedInstance application) {
        List<Deployment> deploymentsToRemove = application.get().productionDeployments().values().stream()
                .filter(deployment -> ! application.get().deploymentSpec().includes(deployment.zone().environment(),
                                                                                    Optional.of(deployment.zone().region())))
                .collect(Collectors.toList());

        if (deploymentsToRemove.isEmpty()) return application;

        if ( ! application.get().validationOverrides().allows(ValidationId.deploymentRemoval, clock.instant()))
            throw new IllegalArgumentException(ValidationId.deploymentRemoval.value() + ": " + application.get() +
                                               " is deployed in " +
                                               deploymentsToRemove.stream()
                                                                   .map(deployment -> deployment.zone().region().value())
                                                                   .collect(Collectors.joining(", ")) +
                                               ", but does not include " +
                                               (deploymentsToRemove.size() > 1 ? "these zones" : "this zone") +
                                               " in deployment.xml. " +
                                               ValidationOverrides.toAllowMessage(ValidationId.deploymentRemoval));

        LockedInstance applicationWithRemoval = application;
        for (Deployment deployment : deploymentsToRemove)
            applicationWithRemoval = deactivate(applicationWithRemoval, deployment.zone());
        return applicationWithRemoval;
    }

    private LockedInstance withoutUnreferencedDeploymentJobs(LockedInstance application) {
        for (JobType job : JobList.from(application.get()).production().mapToList(JobStatus::type)) {
            ZoneId zone = job.zone(controller.system());
            if (application.get().deploymentSpec().includes(zone.environment(), Optional.of(zone.region())))
                continue;
            application = application.withoutDeploymentJob(job);
        }
        return application;
    }

    private DeployOptions withVersion(Version version, DeployOptions options) {
        return new DeployOptions(options.deployDirectly,
                                 Optional.of(version),
                                 options.ignoreValidationErrors,
                                 options.deployCurrentVersion);
    }

    /** Returns the endpoints of the deployment, or empty if the request fails */
    public List<URI> getDeploymentEndpoints(DeploymentId deploymentId) {
        if ( ! get(deploymentId.applicationId())
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
        if ( ! get(id.applicationId())
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
    public Map<ZoneId, Map<ClusterSpec.Id, URI>> clusterEndpoints(ApplicationId id, Collection<ZoneId> zones) {
        Map<ZoneId, Map<ClusterSpec.Id, URI>> deployments = new TreeMap<>(Comparator.comparing(ZoneId::value));
        for (ZoneId zone : zones) {
            var endpoints = clusterEndpoints(new DeploymentId(id, zone));
            if ( ! endpoints.isEmpty())
                deployments.put(zone, endpoints);
        }
        return Collections.unmodifiableMap(deployments);
    }

    /**
     * Deletes the the given application. All known instances of the applications will be deleted.
     *
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     */
    public void deleteApplication(TenantName tenantName, ApplicationName applicationName, Optional<Credentials> credentials) {
        Tenant tenant = controller.tenants().require(tenantName);
        if (tenant.type() != Tenant.Type.user && credentials.isEmpty())
            throw new IllegalArgumentException("Could not delete application '" + tenantName + "." + applicationName + "': No credentials provided");

        // Find all instances of the application
        List<ApplicationId> instances = asList(tenantName).stream()
                                                          .map(Instance::id)
                                                          .filter(id -> id.application().equals(applicationName))
                                                          .collect(Collectors.toList());
        if (instances.size() > 1)
            throw new IllegalArgumentException("Could not delete application; more than one instance present: " + instances);

        // TODO: Make this one transaction when database is moved to ZooKeeper
        instances.forEach(id -> deleteInstance(id, credentials));
    }

    /**
     * Deletes the the given application instance.
     *
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     * @throws NotExistsException if the instance does not exist
     */
    public void deleteInstance(ApplicationId applicationId, Optional<Credentials> credentials) {
        Tenant tenant = controller.tenants().require(applicationId.tenant());
        if (tenant.type() != Tenant.Type.user && credentials.isEmpty())
                throw new IllegalArgumentException("Could not delete application '" + applicationId + "': No credentials provided");

        if (controller.applications().get(applicationId).isEmpty()) {
            throw new NotExistsException("Could not delete application '" + applicationId + "': Application not found");
        }

        lockOrThrow(applicationId, application -> {
            if ( ! application.get().deployments().isEmpty())
                throw new IllegalArgumentException("Could not delete '" + application + "': It has active deployments in: " +
                                                   application.get().deployments().keySet().stream().map(ZoneId::toString)
                                                              .sorted().collect(Collectors.joining(", ")));

            curator.removeInstance(applicationId);
            applicationStore.removeAll(applicationId);
            applicationStore.removeAll(TesterId.of(applicationId));

            application.get().rotations().forEach(assignedRotation -> {
                var endpoints = application.get().endpointsIn(controller.system(), assignedRotation.endpointId());
                endpoints.asList().stream()
                         .map(Endpoint::dnsName)
                         .forEach(name -> {
                             controller.nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordName.from(name), Priority.normal);
                         });
            });

            log.info("Deleted " + application);
        });


        if (   tenant.type() != Tenant.Type.user
            && controller.applications().asList(applicationId.tenant()).stream()
                         .map(application -> application.id().application())
                         .noneMatch(applicationId.application()::equals))
            // TODO jonmv: Implementations ignore the instance — refactor to provide tenant and application names only.
            accessControl.deleteApplication(applicationId, credentials.get());
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
     * Replace any previous version of this application by this instance
     *
     * @param application a locked application to store
     */
    public void store(LockedInstance application) {
        curator.writeInstance(application.get());
    }

    /**
     * Acquire a locked application to modify and store, if there is an application with the given id.
     *
     * @param applicationId ID of the application to lock and get.
     * @param action Function which acts on the locked application.
     */
    public void lockApplicationIfPresent(ApplicationId applicationId, Consumer<LockedApplication> action) {
        try (Lock lock = lock(applicationId)) {
            getApplication(applicationId).map(application -> new LockedApplication(application, lock)).ifPresent(action);
        }
    }

    /**
     * Acquire a locked instance to modify and store, if there is an instance with the given id.
     *
     * @param applicationId ID of the instance to lock and get.
     * @param action Function which acts on the locked instance.
     */
    public void lockIfPresent(ApplicationId applicationId, Consumer<LockedInstance> action) {
        try (Lock lock = lock(applicationId)) {
            get(applicationId).map(instance -> new LockedInstance(instance, lock)).ifPresent(action);
        }
    }

    /**
     * Acquire a locked application to modify and store, or throw an exception if no application has the given id.
     *
     * @param applicationId ID of the application to lock and require.
     * @param action Function which acts on the locked application.
     * @throws IllegalArgumentException when application does not exist.
     */
    public void lockApplicationOrThrow(ApplicationId applicationId, Consumer<LockedApplication> action) {
        try (Lock lock = lock(applicationId)) {
            action.accept(new LockedApplication(requireApplication(applicationId), lock));
        }
    }

    /**
     * Acquire a locked instance to modify and store, or throw an exception if no instance has the given id.
     *
     * @param applicationId ID of the instance to lock and require.
     * @param action Function which acts on the locked instance.
     * @throws IllegalArgumentException when instance does not exist.
     */
    public void lockOrThrow(ApplicationId applicationId, Consumer<LockedInstance> action) {
        try (Lock lock = lock(applicationId)) {
            action.accept(new LockedInstance(require(applicationId), lock));
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
    public void deactivate(ApplicationId application, ZoneId zone) {
        lockOrThrow(application, lockedInstance -> store(deactivate(lockedInstance, zone)));
    }

    /**
     * Deactivates a locked application without storing it
     *
     * @return the application with the deployment in the given zone removed
     */
    private LockedInstance deactivate(LockedInstance application, ZoneId zone) {
        try {
            configServer.deactivate(new DeploymentId(application.get().id(), zone));
        } catch (NotFoundException ignored) {
            // ok; already gone
        } finally {
            routingPolicies.refresh(application.get().id(), application.get().deploymentSpec(), zone);
        }
        return application.withoutDeploymentIn(zone);
    }

    public DeploymentTrigger deploymentTrigger() { return deploymentTrigger; }

    private ApplicationId dashToUnderscore(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value().replaceAll("-", "_"),
                                  id.instance().value());
    }

    /**
     * Returns a lock which provides exclusive rights to changing this application.
     * Any operation which stores an application need to first acquire this lock, then read, modify
     * and store the application, and finally release (close) the lock.
     */
    Lock lock(ApplicationId application) {
        return curator.lock(application);
    }

    /**
     * Returns a lock which provides exclusive rights to deploying this application to the given zone.
     */
    private Lock lockForDeployment(ApplicationId application, ZoneId zone) {
        return curator.lockForDeployment(application, zone);
    }

    /** Verify that we don't downgrade an existing production deployment. */
    private void validateRun(Instance instance, ZoneId zone, Version platformVersion, ApplicationVersion applicationVersion) {
        Deployment deployment = instance.deployments().get(zone);
        if (   zone.environment().isProduction() && deployment != null
            && (   platformVersion.compareTo(deployment.version()) < 0 && ! instance.change().isPinned()
                || applicationVersion.compareTo(deployment.applicationVersion()) < 0))
            throw new IllegalArgumentException(String.format("Rejecting deployment of %s to %s, as the requested versions (platform: %s, application: %s)" +
                                                             " are older than the currently deployed (platform: %s, application: %s).",
                                                             instance, zone, platformVersion, applicationVersion, deployment.version(), deployment.applicationVersion()));
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
     * @param tenantName Tenant where application should be deployed
     * @param applicationPackage Application package
     * @param deployer Principal initiating the deployment, possibly empty
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
