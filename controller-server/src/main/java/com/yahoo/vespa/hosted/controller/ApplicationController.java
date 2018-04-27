// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.NToken;
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
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NoInstanceException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.JobStatus.JobRun;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    private final RotationRepository rotationRepository;
    private final AthenzClientFactory zmsClientFactory;
    private final NameService nameService;
    private final ConfigServer configServer;
    private final RoutingGenerator routingGenerator;
    private final Clock clock;

    private final DeploymentTrigger deploymentTrigger;

    ApplicationController(Controller controller, CuratorDb curator,
                          AthenzClientFactory zmsClientFactory, RotationsConfig rotationsConfig,
                          NameService nameService, ConfigServer configServer,
                          ArtifactRepository artifactRepository,
                          RoutingGenerator routingGenerator, BuildService buildService, Clock clock) {
        this.controller = controller;
        this.curator = curator;
        this.zmsClientFactory = zmsClientFactory;
        this.nameService = nameService;
        this.configServer = configServer;
        this.routingGenerator = routingGenerator;
        this.clock = clock;

        this.artifactRepository = artifactRepository;
        this.rotationRepository = new RotationRepository(rotationsConfig, this, curator);
        this.deploymentTrigger = new DeploymentTrigger(controller, curator, buildService, clock);

        for (Application application : curator.readApplications()) {
            lockIfPresent(application.id(), this::store);
        }
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

    /**
     * Set the rotations marked as 'global' either 'in' or 'out of' service.
     *
     * @return The canonical endpoint altered if any
     * @throws IOException if rotation status cannot be updated
     */
    public List<String> setGlobalRotationStatus(DeploymentId deploymentId, EndpointStatus status) throws IOException {
        List<String> rotations = new ArrayList<>();
        Optional<String> endpoint = getCanonicalGlobalEndpoint(deploymentId);

        if (endpoint.isPresent()) {
            configServer.setGlobalRotationStatus(deploymentId, endpoint.get(), status);
            rotations.add(endpoint.get());
        }

        return rotations;
    }

    /**
     * Get the endpoint status for the global endpoint of this application
     *
     * @return Map between the endpoint and the rotation status
     * @throws IOException if global rotation status cannot be determined
     */
    public Map<String, EndpointStatus> getGlobalRotationStatus(DeploymentId deploymentId) throws IOException {
        Map<String, EndpointStatus> result = new HashMap<>();
        Optional<String> endpoint = getCanonicalGlobalEndpoint(deploymentId);

        if (endpoint.isPresent()) {
            EndpointStatus status = configServer.getGlobalRotationStatus(deploymentId, endpoint.get());
            result.put(endpoint.get(), status);
        }

        return result;
    }

    /**
     * Global rotations (plural as we can have aliases) map to exactly one service endpoint.
     * This method finds that one service endpoint and strips the URI part that
     * the routingGenerator is wrapping around the endpoint.
     *
     * @param deploymentId The deployment to retrieve global service endpoint for
     * @return Empty if no global endpoint exist, otherwise the service endpoint ([clustername.]app.tenant.region.env)
     */
    Optional<String> getCanonicalGlobalEndpoint(DeploymentId deploymentId) throws IOException {
        Map<String, RoutingEndpoint> hostToGlobalEndpoint = new HashMap<>();
        Map<String, String> hostToCanonicalEndpoint = new HashMap<>();

        for (RoutingEndpoint endpoint : routingGenerator.endpoints(deploymentId)) {
            try {
                URI uri = new URI(endpoint.getEndpoint());
                String serviceEndpoint = uri.getHost();
                if (serviceEndpoint == null) {
                    throw new IOException("Unexpected endpoints returned from the Routing Generator");
                }
                String canonicalEndpoint = serviceEndpoint.replaceAll(".vespa.yahooapis.com", "");
                String hostname = endpoint.getHostname();

                // This check is needed until the old implementations of
                // RoutingEndpoints that lacks hostname is gone
                if (hostname != null) {

                    // Book-keeping
                    if (endpoint.isGlobal()) {
                        hostToGlobalEndpoint.put(hostname, endpoint);
                    } else {
                        hostToCanonicalEndpoint.put(hostname, canonicalEndpoint);
                    }

                    // Return as soon as we have a map between a global and a canonical endpoint
                    if (hostToGlobalEndpoint.containsKey(hostname) && hostToCanonicalEndpoint.containsKey(hostname)) {
                        return Optional.of(hostToCanonicalEndpoint.get(hostname));
                    }
                }
            } catch (URISyntaxException use) {
                throw new IOException(use);
            }
        }

        return Optional.empty();
    }


    /**
     * Creates a new application for an existing tenant.
     *
     * @throws IllegalArgumentException if the application already exists
     */
    public Application createApplication(ApplicationId id, Optional<NToken> token) {
        if ( ! (id.instance().isDefault() || id.instance().value().matches("\\d+"))) // TODO: Support instances properly
            throw new UnsupportedOperationException("Only the instance names 'default' and names which are just the PR number are supported at the moment");
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
                    throw new IllegalArgumentException("Could not create '" + id + "': No NToken provided");

                ZmsClient zmsClient = zmsClientFactory.createZmsClientWithAuthorizedServiceToken(token.get());
                zmsClient.addApplication(((AthenzTenant) tenant.get()).domain(),
                                         new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()));
            }
            LockedApplication application = new LockedApplication(new Application(id), lock);
            store(application);
            log.info("Created " + application);
            return application;
        }
    }

    /** Deploys an application. If the application does not exist it is created. */
    // TODO: Get rid of the options arg
    public ActivateResult deploy(ApplicationId applicationId, ZoneId zone,
                                 Optional<ApplicationPackage> applicationPackageFromDeployer,
                                 DeployOptions options) {
        try (Lock lock = lock(applicationId)) {
            LockedApplication application = get(applicationId)
                    .map(app -> new LockedApplication(app, lock))
                    .orElseGet(() -> new LockedApplication(createApplication(applicationId, Optional.empty()), lock));

            boolean canDeployDirectly =   ! options.screwdriverBuildJob.map(job1 -> job1.screwdriverId).isPresent()
                                        ||  zone.environment().isManuallyDeployed();
            boolean preferOldestVersion = options.deployCurrentVersion;

            // Determine versions to use.
            Version platformVersion;
            ApplicationVersion applicationVersion;
            ApplicationPackage applicationPackage;
            if (canDeployDirectly) {
                platformVersion = options.vespaVersion.map(Version::new).orElse(controller.systemVersion());
                applicationVersion = ApplicationVersion.unknown;
                applicationPackage = applicationPackageFromDeployer.orElseThrow(
                        () -> new IllegalArgumentException("Application package must be given when deploying to " + zone));
            } else {
                JobType jobType = JobType.from(controller.system(), zone)
                                         .orElseThrow(() -> new IllegalArgumentException("No job found for zone " + zone));
                Optional<JobStatus> job = Optional.ofNullable(application.deploymentJobs().jobStatus().get(jobType));
                if (    ! job.isPresent()
                     || ! job.get().lastTriggered().isPresent()
                     ||   job.get().lastCompleted().isPresent() && job.get().lastCompleted().get().at().isAfter(job.get().lastTriggered().get().at()))
                    return unexpectedDeployment(applicationId, zone);
                JobRun triggered = job.get().lastTriggered().get();
                platformVersion = preferOldestVersion
                        ? triggered.sourcePlatform().orElse(triggered.platform())
                        : triggered.platform();
                applicationVersion = preferOldestVersion
                        ? triggered.sourceApplication().orElse(triggered.application())
                        : triggered.application();
                applicationPackage = new ApplicationPackage(artifactRepository.getApplicationPackage(application.id(), applicationVersion.id()));
                validateRun(application, zone, platformVersion, applicationVersion);
            }

            validate(applicationPackage.deploymentSpec());

            // Update application with information from application package
            if ( ! preferOldestVersion) {
                // Store information about application package
                application = application.with(applicationPackage.deploymentSpec());
                application = application.with(applicationPackage.validationOverrides());

                // Delete zones not listed in DeploymentSpec, if allowed
                // We do this at deployment time to be able to return a validation failure message when necessary
                application = deleteRemovedDeployments(application);

                // Clean up deployment jobs that are no longer referenced by deployment spec
                application = deleteUnreferencedDeploymentJobs(application);

                store(application); // store missing information even if we fail deployment below
            }

            // Assign global rotation
            application = withRotation(application, zone);
            Set<String> rotationNames = new HashSet<>();
            Set<String> cnames = new HashSet<>();
            application.rotation().ifPresent(applicationRotation -> {
                rotationNames.add(applicationRotation.id().asString());
                cnames.add(applicationRotation.dnsName());
                cnames.add(applicationRotation.secureDnsName());
            });

            // Carry out deployment
            options = withVersion(platformVersion, options);
            ActivateResult result = deploy(applicationId, applicationPackage, zone, options, rotationNames, cnames);
            application = application.withNewDeployment(zone, applicationVersion, platformVersion, clock.instant());
            store(application);
            return result;
        }
    }

    /** Deploy a system application to given zone */
    public void deploy(SystemApplication application, ZoneId zone, Version version) {
        if (!application.hasApplicationPackage()) {
            // Deploy by calling node repository directly
            configServer().nodeRepository().upgrade(zone, application.nodeType(), version);
            return;
        }
        ApplicationPackage applicationPackage = new ApplicationPackage(
                artifactRepository.getSystemApplicationPackage(application.id(), zone, version)
        );
        DeployOptions options = withVersion(version, DeployOptions.none());
        deploy(application.id(), applicationPackage, zone, options, Collections.emptySet(), Collections.emptySet());
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
        if (zone.environment() == Environment.prod && application.deploymentSpec().globalServiceId().isPresent()) {
            try (RotationLock rotationLock = rotationRepository.lock()) {
                Rotation rotation = rotationRepository.getOrAssignRotation(application, rotationLock);
                application = application.with(rotation.id());
                store(application); // store assigned rotation even if deployment fails

                registerRotationInDns(rotation, application.rotation().get().dnsName());
                registerRotationInDns(rotation, application.rotation().get().secureDnsName());
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

    private LockedApplication deleteRemovedDeployments(LockedApplication application) {
        List<Deployment> deploymentsToRemove = application.productionDeployments().values().stream()
                .filter(deployment -> ! application.deploymentSpec().includes(deployment.zone().environment(),
                                                                              Optional.of(deployment.zone().region())))
                .collect(Collectors.toList());

        if (deploymentsToRemove.isEmpty()) return application;

        if ( ! application.validationOverrides().allows(ValidationId.deploymentRemoval, clock.instant()))
            throw new IllegalArgumentException(ValidationId.deploymentRemoval.value() + ": " + application +
                                               " is deployed in " +
                                               deploymentsToRemove.stream()
                                                                   .map(deployment -> deployment.zone().region().value())
                                                                   .collect(Collectors.joining(", ")) +
                                               ", but does not include " +
                                               (deploymentsToRemove.size() > 1 ? "these zones" : "this zone") +
                                               " in deployment.xml");

        LockedApplication applicationWithRemoval = application;
        for (Deployment deployment : deploymentsToRemove)
            applicationWithRemoval = deactivate(applicationWithRemoval, deployment.zone());
        return applicationWithRemoval;
    }

    private LockedApplication deleteUnreferencedDeploymentJobs(LockedApplication application) {
        for (JobType job : application.deploymentJobs().jobStatus().keySet()) {
            Optional<ZoneId> zone = job.zone(controller.system());

            if ( ! job.isProduction() || (zone.isPresent() && application.deploymentSpec().includes(zone.get().environment(), zone.map(ZoneId::region))))
                continue;
            application = application.withoutDeploymentJob(job);
        }
        return application;
    }

    private DeployOptions withVersion(Version version, DeployOptions options) {
        return new DeployOptions(options.screwdriverBuildJob,
                                 Optional.of(version),
                                 options.ignoreValidationErrors,
                                 options.deployCurrentVersion);
    }

    /** Register a DNS name for rotation */
    private void registerRotationInDns(Rotation rotation, String dnsName) {
        try {
            Optional<Record> record = nameService.findRecord(Record.Type.CNAME, RecordName.from(dnsName));
            RecordData rotationName = RecordData.fqdn(rotation.name());
            if (record.isPresent()) {
                // Ensure that the existing record points to the correct rotation
                if ( ! record.get().data().equals(rotationName)) {
                    nameService.updateRecord(record.get().id(), rotationName);
                    log.info("Updated mapping for record ID " + record.get().id().asString() + ": '" + dnsName
                             + "' -> '" + rotation.name() + "'");
                }
            } else {
                RecordId id = nameService.createCname(RecordName.from(dnsName), rotationName);
                log.info("Registered mapping with record ID " + id.asString() + ": '" + dnsName + "' -> '"
                         + rotation.name() + "'");
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to register CNAME", e);
        }
    }

    /** Returns the endpoints of the deployment, or an empty list if the request fails */
    public Optional<List<URI>> getDeploymentEndpoints(DeploymentId deploymentId) {
        try {
            return Optional.of(ImmutableList.copyOf(routingGenerator.endpoints(deploymentId).stream()
                                                                    .map(RoutingEndpoint::getEndpoint)
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
    public void deleteApplication(ApplicationId applicationId, Optional<NToken> token) {
        // Find all instances of the application
        List<ApplicationId> instances = controller.applications().asList(applicationId.tenant())
                                                  .stream()
                                                  .map(Application::id)
                                                  .filter(id -> id.application().equals(applicationId.application()) &&
                                                                id.tenant().equals(applicationId.tenant()))
                                                  .collect(Collectors.toList());
        if (instances.isEmpty()) {
            throw new NotExistsException("Could not delete application '" + applicationId + "': Application not found");
        }

        // TODO: Make this one transaction when database is moved to ZooKeeper
        instances.forEach(id -> lockOrThrow(id, application -> {
            if ( ! application.deployments().isEmpty())
                throw new IllegalArgumentException("Could not delete '" + application + "': It has active deployments");

            Tenant tenant = controller.tenants().tenant(id.tenant()).get();
            if (tenant instanceof AthenzTenant && ! token.isPresent())
                throw new IllegalArgumentException("Could not delete '" + application + "': No NToken provided");

            // Only delete in Athenz once
            if (id.instance().isDefault() && tenant instanceof AthenzTenant) {
                zmsClientFactory.createZmsClientWithAuthorizedServiceToken(token.get())
                                .deleteApplication(((AthenzTenant) tenant).domain(),
                                                   new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()));
            }
            curator.removeApplication(id);

            log.info("Deleted " + application);
        }));
    }

    /**
     * Replace any previous version of this application by this instance
     *
     * @param application a locked application to store
     */
    public void store(LockedApplication application) {
        curator.writeApplication(application);
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
        try {
            configServer.restart(deploymentId, hostname);
        }
        catch (NoInstanceException e) {
            throw new IllegalArgumentException("Could not restart " + deploymentId + ": No such deployment");
        }
    }

    /** Deactivate application in the given zone */
    public void deactivate(Application application, ZoneId zone) {
        lockOrThrow(application.id(), lockedApplication -> store(deactivate(lockedApplication, zone)));
    }

    /**
     * Deactivates a locked application without storing it
     *
     * @return the application with the deployment in the given zone removed
     */
    private LockedApplication deactivate(LockedApplication application, ZoneId zone) {
        try {
            configServer.deactivate(new DeploymentId(application.id(), zone));
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
        return curator.lock(application, Duration.ofMinutes(10));
    }

    /** Verify that each of the production zones listed in the deployment spec exist in this system. */
    private void validate(DeploymentSpec deploymentSpec) {
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
            && (   platformVersion.compareTo(deployment.version()) < 0
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

}
