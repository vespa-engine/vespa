// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NoInstanceException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.athenz.impl.ZmsClientFacade;
import com.yahoo.vespa.hosted.controller.concurrent.Once;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentSteps;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    private final ZmsClientFacade zmsClient;
    private final NameService nameService;
    private final ConfigServer configServer;
    private final RoutingGenerator routingGenerator;
    private final Clock clock;

    private final DeploymentTrigger deploymentTrigger;

    ApplicationController(Controller controller, CuratorDb curator,
                          AthenzClientFactory zmsClientFactory, RotationsConfig rotationsConfig,
                          NameService nameService, ConfigServer configServer,
                          ArtifactRepository artifactRepository, ApplicationStore applicationStore,
                          RoutingGenerator routingGenerator, BuildService buildService, Clock clock) {
        this.controller = controller;
        this.curator = curator;
        this.zmsClient = new ZmsClientFacade(zmsClientFactory.createZmsClient(), zmsClientFactory.getControllerIdentity());
        this.nameService = nameService;
        this.configServer = configServer;
        this.routingGenerator = routingGenerator;
        this.clock = clock;

        this.artifactRepository = artifactRepository;
        this.applicationStore = applicationStore;
        this.rotationRepository = new RotationRepository(rotationsConfig, this, curator);
        this.deploymentTrigger = new DeploymentTrigger(controller, buildService, clock);

        // Update serialization format of all applications
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = clock.instant();
            int count = 0;
            for (Application application : curator.readApplications()) {
                lockIfPresent(application.id(), this::store);
                count++;
            }
            log.log(Level.INFO, String.format("Wrote %d applications in %s", count,
                                              Duration.between(start, clock.instant())));
        });
    }

    /** Returns the application with the given id, or null if it is not present */
    public Optional<Application> get(ApplicationId id) {
        return curator.readApplication(id);
    }

    /**
     * Returns the application with the given id
     *
     * @throws IllegalArgumentException if it does not exist
     */
    public Application require(ApplicationId id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException(id + " not found"));
    }

    /** Returns a snapshot of all applications */
    public List<Application> asList() {
        return sort(curator.readApplications());
    }

    /** Returns all applications of a tenant */
    public List<Application> asList(TenantName tenant) {
        return sort(curator.readApplications(tenant));
    }

    public ArtifactRepository artifacts() { return artifactRepository; }

    public ApplicationStore applicationStore() {  return applicationStore; }

    /** Returns the oldest Vespa version installed on any active or reserved production node for the given application. */
    public Version oldestInstalledPlatform(ApplicationId id) {
        return get(id).flatMap(application -> application.productionDeployments().keySet().stream()
                                                         .flatMap(zone -> configServer().nodeRepository().list(zone, id, EnumSet.of(active, reserved)).stream())
                                                         .map(Node::currentVersion)
                                                         .filter(version -> ! version.isEmpty())
                                                         .min(naturalOrder()))
                      .orElse(controller.systemVersion());
    }

    /** Change the global endpoint status for given deployment */
    public void setGlobalRotationStatus(DeploymentId deployment, EndpointStatus status) {
        findGlobalEndpoint(deployment).map(endpoint -> {
            try {
                configServer.setGlobalRotationStatus(deployment, endpoint.upstreamName(), status);
                return endpoint;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to set rotation status of " + deployment, e);
            }
        }).orElseThrow(() -> new IllegalArgumentException("No global endpoint exists for " + deployment));
    }

    /** Get global endpoint status for given deployment */
    public Map<RoutingEndpoint, EndpointStatus> globalRotationStatus(DeploymentId deployment) {
        return findGlobalEndpoint(deployment).map(endpoint -> {
            try {
                EndpointStatus status = configServer.getGlobalRotationStatus(deployment, endpoint.upstreamName());
                return Collections.singletonMap(endpoint, status);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to get rotation status of " + deployment, e);
            }
        }).orElseGet(Collections::emptyMap);
    }

    /** Find the global endpoint of given deployment, if any */
    public Optional<RoutingEndpoint> findGlobalEndpoint(DeploymentId deployment) {
        return routingGenerator.endpoints(deployment).stream()
                               .filter(RoutingEndpoint::isGlobal)
                               .findFirst();
    }

    /**
     * Creates a new application for an existing tenant.
     *
     * @throws IllegalArgumentException if the application already exists
     */
    public Application createApplication(ApplicationId id, Optional<OktaAccessToken> token) {
        if ( ! (id.instance().isDefault())) // TODO: Support instances properly
            throw new IllegalArgumentException("Only the instance name 'default' is supported at the moment");
        if (id.instance().isTester())
            throw new IllegalArgumentException("'" + id + "' is a tester application!");
        try (Lock lock = lock(id)) {
            // Validate only application names which do not already exist.
            if (asList(id.tenant()).stream().noneMatch(application -> application.id().application().equals(id.application())))
                com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId.validate(id.application().value());

            Optional<Tenant> tenant = controller.tenants().tenant(id.tenant());
            if ( ! tenant.isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': This tenant does not exist");
            if (get(id).isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': Application already exists");
            if (get(dashToUnderscore(id)).isPresent()) // VESPA-1945
                throw new IllegalArgumentException("Could not create '" + id + "': Application " + dashToUnderscore(id) + " already exists");
            if (id.instance().isDefault() && tenant.get() instanceof AthenzTenant) { // Only create the athenz application for "default" instances.
                if ( ! token.isPresent())
                    throw new IllegalArgumentException("Could not create '" + id + "': No Okta Access Token provided");

                zmsClient.addApplication(((AthenzTenant) tenant.get()).domain(),
                                         new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()), token.get());
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
    // TODO jvenstad: Split this, and choose between deployDirectly and deploy in handler, excluding internally built from the latter.
    public ActivateResult deploy(ApplicationId applicationId, ZoneId zone,
                                 Optional<ApplicationPackage> applicationPackageFromDeployer,
                                 Optional<ApplicationVersion> applicationVersionFromDeployer,
                                 DeployOptions options,
                                 Optional<AthenzIdentity> deployingIdentity) {
        if (applicationId.instance().isTester())
            throw new IllegalArgumentException("'" + applicationId + "' is a tester application!");

        try (Lock deploymentLock = lockForDeployment(applicationId, zone)) {
            Version platformVersion;
            ApplicationVersion applicationVersion;
            ApplicationPackage applicationPackage;
            Set<String> rotationNames = new HashSet<>();
            Set<String> cnames = new HashSet<>();

            try (Lock lock = lock(applicationId)) {
                LockedApplication application = get(applicationId)
                        .map(app -> new LockedApplication(app, lock))
                        .orElseGet(() -> new LockedApplication(createApplication(applicationId, Optional.empty()), lock));

                boolean manuallyDeployed = options.deployDirectly || zone.environment().isManuallyDeployed();
                boolean preferOldestVersion = options.deployCurrentVersion;

                // Determine versions to use.
                if (manuallyDeployed) {
                    applicationVersion = applicationVersionFromDeployer.orElse(ApplicationVersion.unknown);
                    applicationPackage = applicationPackageFromDeployer.orElseThrow(
                            () -> new IllegalArgumentException("Application package must be given when deploying to " + zone));
                    platformVersion = options.vespaVersion.map(Version::new).orElse(applicationPackage.deploymentSpec().majorVersion()
                                                                                                      .flatMap(this::lastCompatibleVersion)
                                                                                                      .orElse(controller.systemVersion()));
                }
                else {
                    JobType jobType = JobType.from(controller.system(), zone)
                                             .orElseThrow(() -> new IllegalArgumentException("No job is known for " + zone + "."));
                    Optional<JobStatus> job = Optional.ofNullable(application.get().deploymentJobs().jobStatus().get(jobType));
                    if (   ! job.isPresent()
                        || ! job.get().lastTriggered().isPresent()
                        ||   job.get().lastCompleted().isPresent() && job.get().lastCompleted().get().at().isAfter(job.get().lastTriggered().get().at()))
                        return unexpectedDeployment(applicationId, zone);
                    JobRun triggered = job.get().lastTriggered().get();
                    platformVersion = preferOldestVersion ? triggered.sourcePlatform().orElse(triggered.platform())
                                                          : triggered.platform();
                    applicationVersion = preferOldestVersion ? triggered.sourceApplication().orElse(triggered.application())
                                                             : triggered.application();

                    applicationPackage = getApplicationPackage(application.get(), applicationVersion);
                    validateRun(application.get(), zone, platformVersion, applicationVersion);
                }

                // TODO: Remove this when all packages are validated upon submission, as in ApplicationApiHandler.submit(...).
                verifyApplicationIdentityConfiguration(applicationId.tenant(), applicationPackage, deployingIdentity);

                // Assign global rotation
                application = withRotation(application, zone);
                Application app = application.get();
                app.globalDnsName(controller.system()).ifPresent(applicationRotation -> {
                    rotationNames.add(app.rotation().orElseThrow(() -> new RuntimeException("Global Dns assigned, but no rotation id present")).asString());
                    cnames.add(applicationRotation.dnsName());
                    cnames.add(applicationRotation.secureDnsName());
                    cnames.add(applicationRotation.oathDnsName());
                });

                // Update application with information from application package
                if (   ! preferOldestVersion
                    && ! application.get().deploymentJobs().deployedInternally()
                    && ! zone.environment().isManuallyDeployed())
                    // TODO jvenstad: Store only on submissions
                    storeWithUpdatedConfig(application, applicationPackage);
            } // Release application lock while doing the deployment, which is a lengthy task.

            // Carry out deployment without holding the application lock.
            options = withVersion(platformVersion, options);
            ActivateResult result = deploy(applicationId, applicationPackage, zone, options, rotationNames, cnames);

            lockOrThrow(applicationId, application ->
                    store(application.withNewDeployment(zone, applicationVersion, platformVersion, clock.instant())));
            return result;
        }
    }

    /** Fetches the requested application package from the artifact store(s). */
    public ApplicationPackage getApplicationPackage(Application application, ApplicationVersion version) {
        try {
            return application.deploymentJobs().deployedInternally()
                    ? new ApplicationPackage(applicationStore.get(application.id(), version))
                    : new ApplicationPackage(artifactRepository.getApplicationPackage(application.id(), version.id()));
        }
        catch (RuntimeException e) { // If application has switched deployment pipeline, artifacts stored prior to the switch are in the other artifact store.
            try {
                log.info("Fetching application package for " + application.id() + " from alternate repository; it is now deployed "
                         + (application.deploymentJobs().deployedInternally() ? "internally" : "externally") + "\nException was: " + Exceptions.toMessageString(e));
                return application.deploymentJobs().deployedInternally()
                        ? new ApplicationPackage(artifactRepository.getApplicationPackage(application.id(), version.id()))
                        : new ApplicationPackage(applicationStore.get(application.id(), version));
            }
            catch (RuntimeException s) { // If this fails, too, the first failure is most likely the relevant one.
                e.addSuppressed(s);
                throw e;
            }
        }
    }

    /** Stores the deployment spec and validation overrides from the application package, and runs cleanup. */
    public LockedApplication storeWithUpdatedConfig(LockedApplication application, ApplicationPackage applicationPackage) {
        validate(applicationPackage.deploymentSpec());

        application = application.with(applicationPackage.deploymentSpec());
        application = application.with(applicationPackage.validationOverrides());

        // Delete zones not listed in DeploymentSpec, if allowed
        // We do this at deployment time for externally built applications, and at submission time
        // for internally built ones, to be able to return a validation failure message when necessary
        application = withoutDeletedDeployments(application);

        // Clean up deployment jobs that are no longer referenced by deployment spec
        application = withoutUnreferencedDeploymentJobs(application);

        store(application);
        return(application);
    }

    /** Deploy a system application to given zone */
    public void deploy(SystemApplication application, ZoneId zone, Version version) {
        if (application.hasApplicationPackage()) {
            deploySystemApplicationPackage(application, zone, version);
        } else {
            // Deploy by calling node repository directly
            application.nodeTypes().forEach(nodeType -> configServer().nodeRepository().upgrade(zone, nodeType, version));
        }
    }

    /** Deploy a system application to given zone */
    public ActivateResult deploySystemApplicationPackage(SystemApplication application, ZoneId zone, Version version) {
        if (application.hasApplicationPackage()) {
            ApplicationPackage applicationPackage = new ApplicationPackage(
                    artifactRepository.getSystemApplicationPackage(application.id(), zone, version)
            );
            DeployOptions options = withVersion(version, DeployOptions.none());
            return deploy(application.id(), applicationPackage, zone, options, Collections.emptySet(), Collections.emptySet());
        } else {
           throw new RuntimeException("This system application does not have an application package: " + application.id().toShortString());
        }
    }

    /** Deploys the given tester application to the given zone. */
    public ActivateResult deployTester(TesterId tester, ApplicationPackage applicationPackage, ZoneId zone, DeployOptions options) {
        return deploy(tester.id(), applicationPackage, zone, options, Collections.emptySet(), Collections.emptySet());
    }

    private ActivateResult deploy(ApplicationId application, ApplicationPackage applicationPackage,
                                  ZoneId zone, DeployOptions deployOptions,
                                  Set<String> rotationNames, Set<String> cnames) {
        DeploymentId deploymentId = new DeploymentId(application, zone);
        ConfigServer.PreparedApplication preparedApplication =
                configServer.deploy(deploymentId, deployOptions, cnames, rotationNames,
                                    applicationPackage.zippedContent());
        return new ActivateResult(new RevisionId(applicationPackage.hash()), preparedApplication.prepareResponse(),
                                  applicationPackage.zippedContent().length);
    }

    /** Makes sure the application has a global rotation, if eligible. */
    private LockedApplication withRotation(LockedApplication application, ZoneId zone) {
        if (zone.environment() == Environment.prod && application.get().deploymentSpec().globalServiceId().isPresent()) {
            try (RotationLock rotationLock = rotationRepository.lock()) {
                Rotation rotation = rotationRepository.getOrAssignRotation(application.get(), rotationLock);
                application = application.with(rotation.id());
                store(application); // store assigned rotation even if deployment fails

                registerRotationInDns(rotation, application.get().globalDnsName(controller.system()).get().dnsName());
                registerRotationInDns(rotation, application.get().globalDnsName(controller.system()).get().secureDnsName());
                registerRotationInDns(rotation, application.get().globalDnsName(controller.system()).get().oathDnsName());
            }
        }
        return application;
    }

    private ActivateResult unexpectedDeployment(ApplicationId applicationId, ZoneId zone) {

        Log logEntry = new Log();
        logEntry.level = "WARNING";
        logEntry.time = clock.instant().toEpochMilli();
        logEntry.message = "Ignoring deployment of " + require(applicationId) + " to " + zone +
                           " as a deployment is not currently expected";
        PrepareResponse prepareResponse = new PrepareResponse();
        prepareResponse.log = Collections.singletonList(logEntry);
        prepareResponse.configChangeActions = new ConfigChangeActions(Collections.emptyList(), Collections.emptyList());
        return new ActivateResult(new RevisionId("0"), prepareResponse, 0);
    }

    private LockedApplication withoutDeletedDeployments(LockedApplication application) {
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

        LockedApplication applicationWithRemoval = application;
        for (Deployment deployment : deploymentsToRemove)
            applicationWithRemoval = deactivate(applicationWithRemoval, deployment.zone());
        return applicationWithRemoval;
    }

    private LockedApplication withoutUnreferencedDeploymentJobs(LockedApplication application) {
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

    /** Register a DNS name for rotation */
    private void registerRotationInDns(Rotation rotation, String dnsName) {
        try {

            RecordData rotationName = RecordData.fqdn(rotation.name());
            List<Record> records = nameService.findRecords(Record.Type.CNAME, RecordName.from(dnsName));
            records.forEach(record -> {
                // Ensure that the existing record points to the correct rotation
                if ( ! record.data().equals(rotationName)) {
                    nameService.updateRecord(record, rotationName);
                    log.info("Updated mapping for record '" + record + "': '" + dnsName
                             + "' -> '" + rotation.name() + "'");
                }
            });

            if (records.isEmpty()) {
                Record record = nameService.createCname(RecordName.from(dnsName), rotationName);
                log.info("Registered mapping as record  '" + record + "'");
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to register CNAME", e);
        }
    }

    /** Returns the endpoints of the deployment, or an empty list if the request fails */
    public Optional<List<URI>> getDeploymentEndpoints(DeploymentId deploymentId) {
        if ( ! get(deploymentId.applicationId())
                .map(application -> application.deployments().containsKey(deploymentId.zoneId()))
                .orElse(deploymentId.applicationId().instance().isTester()))
            throw new NotExistsException("Deployment", deploymentId.toString());

        try {
            return Optional.of(ImmutableList.copyOf(routingGenerator.endpoints(deploymentId).stream()
                                                                    .map(RoutingEndpoint::endpoint)
                                                                    .map(URI::create)
                                                                    .iterator()));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to get endpoint information for " + deploymentId + ": "
                                   + Exceptions.toMessageString(e));
            return Optional.empty();
        }
    }

    /**
     * Deletes the the given application. All known instances of the applications will be deleted,
     * including PR instances.
     *
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     * @throws NotExistsException if no instances of the application exist
     */
    public void deleteApplication(ApplicationId applicationId, Optional<OktaAccessToken> token) {
        // Find all instances of the application
        List<ApplicationId> instances = asList(applicationId.tenant()).stream()
                                                                      .map(Application::id)
                                                                      .filter(id -> id.application().equals(applicationId.application()))
                                                                      .collect(Collectors.toList());
        if (instances.isEmpty()) {
            throw new NotExistsException("Could not delete application '" + applicationId + "': Application not found");
        }

        // TODO: Make this one transaction when database is moved to ZooKeeper
        instances.forEach(id -> lockOrThrow(id, application -> {
            if ( ! application.get().deployments().isEmpty())
                throw new IllegalArgumentException("Could not delete '" + application + "': It has active deployments");

            Tenant tenant = controller.tenants().tenant(id.tenant()).get();
            if (tenant instanceof AthenzTenant && ! token.isPresent())
                throw new IllegalArgumentException("Could not delete '" + application + "': No Okta Access Token provided");

            // Only delete in Athenz once
            if (id.instance().isDefault() && tenant instanceof AthenzTenant) {
                zmsClient.deleteApplication(((AthenzTenant) tenant).domain(),
                                                   new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()), token.get());
            }
            curator.removeApplication(id);
            applicationStore.removeAll(id);
            applicationStore.removeAll(TesterId.of(id));

            log.info("Deleted " + application);
        }));
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
    public void lockIfPresent(ApplicationId applicationId, Consumer<LockedApplication> action) {
        try (Lock lock = lock(applicationId)) {
            get(applicationId).map(application -> new LockedApplication(application, lock)).ifPresent(action);
        }
    }

    /**
     * Acquire a locked application to modify and store, or throw an exception if no application has the given id.
     *
     * @param applicationId ID of the application to lock and require.
     * @param action Function which acts on the locked application.
     * @throws IllegalArgumentException when application does not exist.
     */
    public void lockOrThrow(ApplicationId applicationId, Consumer<LockedApplication> action) {
        try (Lock lock = lock(applicationId)) {
            action.accept(new LockedApplication(require(applicationId), lock));
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
        lockOrThrow(application, lockedApplication -> store(deactivate(lockedApplication, zone)));
    }

    /**
     * Deactivates a locked application without storing it
     *
     * @return the application with the deployment in the given zone removed
     */
    private LockedApplication deactivate(LockedApplication application, ZoneId zone) {
        try {
            configServer.deactivate(new DeploymentId(application.get().id(), zone));
        }
        catch (NoInstanceException ignored) {
            // ok; already gone
        }
        return application.withoutDeploymentIn(zone);
    }

    public DeploymentTrigger deploymentTrigger() { return deploymentTrigger; }

    private ApplicationId dashToUnderscore(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value().replaceAll("-", "_"),
                                  id.instance().value());
    }

    public ConfigServer configServer() { return configServer; }

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

    /** Verify that each of the production zones listed in the deployment spec exist in this system. */
    private void validate(DeploymentSpec deploymentSpec) {
        new DeploymentSteps(deploymentSpec, controller::system).jobs();
        deploymentSpec.zones().stream()
                .filter(zone -> zone.environment() == Environment.prod)
                .forEach(zone -> {
                    if ( ! controller.zoneRegistry().hasZone(ZoneId.from(zone.environment(),
                                                                         zone.region().orElse(null)))) {
                        throw new IllegalArgumentException("Zone " + zone + " in deployment spec was not found in this system!");
                    }
                });
    }

    /** Verify that we don't downgrade an existing production deployment. */
    private void validateRun(Application application, ZoneId zone, Version platformVersion, ApplicationVersion applicationVersion) {
        Deployment deployment = application.deployments().get(zone);
        if (   zone.environment().isProduction() && deployment != null
            && (   platformVersion.compareTo(deployment.version()) < 0 && ! application.change().isPinned()
                || applicationVersion.compareTo(deployment.applicationVersion()) < 0))
            throw new IllegalArgumentException(String.format("Rejecting deployment of %s to %s, as the requested versions (platform: %s, application: %s)" +
                                                             " are older than the currently deployed (platform: %s, application: %s).",
                                                             application, zone, platformVersion, applicationVersion, deployment.version(), deployment.applicationVersion()));
    }

    public RotationRepository rotationRepository() {
        return rotationRepository;
    }

    /** Sort given list of applications by application ID */
    private static List<Application> sort(List<Application> applications) {
        return applications.stream().sorted(Comparator.comparing(Application::id)).collect(Collectors.toList());
    }

    /**
     * Verifies that the application can be deployed to the tenant, following these rules:
     *
     * 1. If the principal is given, verify that the principal is tenant admin or admin of the tenant domain
     * 2. If the principal is not given, verify that the Athenz domain of the tenant equals Athenz domain given in deployment.xml
     *
     * @param tenantName Tenant where application should be deployed
     * @param applicationPackage Application package
     * @param deployingIdentity Principal initiating the deployment, possibly empty
     */
    public void verifyApplicationIdentityConfiguration(TenantName tenantName, ApplicationPackage applicationPackage, Optional<AthenzIdentity> deployingIdentity) {
        applicationPackage.deploymentSpec().athenzDomain()
                          .ifPresent(identityDomain -> {
                              Optional<Tenant> tenant = controller.tenants().tenant(tenantName);
                              if(!tenant.isPresent()) {
                                  throw new IllegalArgumentException("Tenant does not exist");
                              } else {
                                  if (isUserDeployment(deployingIdentity)) {
                                      deployingIdentity
                                              .filter(user -> zmsClient.hasTenantAdminAccess(user, new AthenzDomain(identityDomain.value())))
                                              .orElseThrow(() -> new IllegalArgumentException(
                                                      String.format("User %s is not allowed to launch services in Athenz domain %s. Please reach out to the domain admin.", deployingIdentity.get().getFullName(), identityDomain.value())
                                              ));
                                  } else {
                                      AthenzDomain tenantDomain = tenant.filter(t -> t instanceof AthenzTenant)
                                              .map(t -> (AthenzTenant) t)
                                              .orElseThrow(() -> new IllegalArgumentException(
                                                      String.format("Athenz domain defined in deployment.xml, but no Athenz domain for tenant (%s). " +
                                                                    tenantName.value())))
                                              .domain();

                                      if (!Objects.equals(tenantDomain.getName(), identityDomain.value()))
                                          throw new IllegalArgumentException(String.format("Athenz domain in deployment.xml: [%s] must match tenant domain: [%s]",
                                                                                           identityDomain.value(),
                                                                                           tenantDomain.getName()));
                                  }
                              }
                          });
    }

    /** Returns the latest known version within the given major. */
    private Optional<Version> lastCompatibleVersion(int targetMajorVersion) {
        return controller.versionStatus().versions().stream()
                         .map(VespaVersion::versionNumber)
                         .filter(version -> version.getMajor() == targetMajorVersion)
                         .max(naturalOrder());
    }

    private boolean isUserDeployment(Optional<AthenzIdentity> identity) {
        return identity
                .filter(id -> id instanceof AthenzUser)
                .isPresent();
    }
}
