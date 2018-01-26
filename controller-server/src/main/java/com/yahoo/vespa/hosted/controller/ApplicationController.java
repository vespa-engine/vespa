// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.InstanceEndpoints;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.GitRevision;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerClient;
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
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.DeploymentExpirer;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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

    /** For permanent storage */
    private final ControllerDb db;

    /** For working memory storage and sharing between controllers */
    private final CuratorDb curator;

    private final ArtifactRepository artifactRepository;
    private final RotationRepository rotationRepository;
    private final AthenzClientFactory zmsClientFactory;
    private final NameService nameService;
    private final ConfigServerClient configserverClient;
    private final RoutingGenerator routingGenerator;
    private final Clock clock;

    private final DeploymentTrigger deploymentTrigger;

    ApplicationController(Controller controller, ControllerDb db, CuratorDb curator,
                          AthenzClientFactory zmsClientFactory, RotationsConfig rotationsConfig,
                          NameService nameService, ConfigServerClient configserverClient,
                          ArtifactRepository artifactRepository,
                          RoutingGenerator routingGenerator, Clock clock) {
        this.controller = controller;
        this.db = db;
        this.curator = curator;
        this.zmsClientFactory = zmsClientFactory;
        this.nameService = nameService;
        this.configserverClient = configserverClient;
        this.routingGenerator = routingGenerator;
        this.clock = clock;

        this.artifactRepository = artifactRepository;
        this.rotationRepository = new RotationRepository(rotationsConfig, this, curator);
        this.deploymentTrigger = new DeploymentTrigger(controller, curator, clock);

        for (Application application : db.listApplications()) {
            lockIfPresent(application.id(), this::store);
        }
    }

    /** Returns the application with the given id, or null if it is not present */
    public Optional<Application> get(ApplicationId id) {
        return db.getApplication(id);
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
        return db.listApplications();
    }

    /** Returns all applications of a tenant */
    public List<Application> asList(TenantName tenant) {
        return db.listApplications(new TenantId(tenant.value()));
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
            configserverClient.setGlobalRotationStatus(deploymentId, endpoint.get(), status);
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
            EndpointStatus status = configserverClient.getGlobalRotationStatus(deploymentId, endpoint.get());
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
        // TODO: TLS: PR names change to prXXX.
        if ( ! (   id.instance().value().equals("default")
                || id.instance().value().matches("^default-pr\\d+$") // TODO: Remove when these no longer deploy.
                || id.instance().value().matches("^\\d+$"))) // TODO: Support instances properly
            throw new UnsupportedOperationException("Only the instance names 'default' and names starting with 'default-pr' or which are just the PR number are supported at the moment");
        try (Lock lock = lock(id)) {
            com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId.validate(id.application().value());

            Optional<Tenant> tenant = controller.tenants().tenant(new TenantId(id.tenant().value()));
            if ( ! tenant.isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': This tenant does not exist");
            if (get(id).isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': Application already exists");
            if (get(dashToUnderscore(id)).isPresent()) // VESPA-1945
                throw new IllegalArgumentException("Could not create '" + id + "': Application " + dashToUnderscore(id) + " already exists");
            if (tenant.get().isAthensTenant() && ! token.isPresent())
                throw new IllegalArgumentException("Could not create '" + id + "': No NToken provided");
            if (tenant.get().isAthensTenant()) {
                ZmsClient zmsClient = zmsClientFactory.createZmsClientWithAuthorizedServiceToken(token.get());
                try {
                    zmsClient.deleteApplication(tenant.get().getAthensDomain().get(),
                                                new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()));
                }
                catch (ZmsException ignored) {
                }
                zmsClient.addApplication(tenant.get().getAthensDomain().get(),
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
    public ActivateResult deployApplication(ApplicationId applicationId, ZoneId zone,
                                            Optional<ApplicationPackage> applicationPackageFromDeployer,
                                            DeployOptions options) {
        try (Lock lock = lock(applicationId)) {
            // TODO: Move application creation outside, to the deploy call in the handler.
            LockedApplication application = get(applicationId)
                    .map(app -> new LockedApplication(app, lock))
                    .orElseGet(() -> {
                        com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId.validate(applicationId.application().value());
                        return new LockedApplication(new Application(applicationId), lock);
                    });

            // Determine Vespa version to use
            Version version;
            if (options.deployCurrentVersion) {
                version = application.versionIn(zone, controller);
            } else if (canDeployDirectlyTo(zone, options)) {
                version = options.vespaVersion.map(Version::new).orElse(controller.systemVersion());
            } else if ( ! application.deploying().isPresent() && ! zone.environment().isManuallyDeployed()) {
                return unexpectedDeployment(applicationId, zone, applicationPackageFromDeployer);
            } else {
                version = application.deployVersionIn(zone, controller);
            }

            Optional<DeploymentJobs.JobType> jobType = DeploymentJobs.JobType.from(controller.system(), zone);
            if (!jobType.isPresent() && !applicationPackageFromDeployer.isPresent()) {
                throw new IllegalArgumentException("Unable to determine job type from zone '" + zone +
                                                   "' and no application package was given");
            }

            // Determine which application package to use
            ApplicationPackage applicationPackage;
            ApplicationVersion applicationVersion;
            if (applicationPackageFromDeployer.isPresent()) {
                applicationVersion = toApplicationPackageRevision(applicationPackageFromDeployer.get(),
                                                                  options.screwdriverBuildJob);
                applicationPackage = applicationPackageFromDeployer.get();
            } else {
                applicationVersion = application.deployApplicationVersion(jobType.get(), controller)
                                                .orElseThrow(() -> new IllegalArgumentException("Cannot determine application version to use in " + zone));
                applicationPackage = new ApplicationPackage(artifactRepository.getApplicationPackage(
                        applicationId, applicationVersion.id())
                );
            }

            validate(applicationPackage.deploymentSpec());

            // TODO: Remove after introducing new application version number
            if ( ! options.deployCurrentVersion && applicationPackageFromDeployer.isPresent()) {
                if (application.deploying().application().isPresent()) {
                    application = application.withDeploying(application.deploying().with(applicationVersion));
                }
                if (!canDeployDirectlyTo(zone, options) && jobType.isPresent()) {
                    // Update with (potentially) missing information about what we triggered:
                    // * When someone else triggered the job, we need to store a stand-in triggering event.
                    // * When this is the system test job, we need to record the new application version,
                    // for future use.
                    JobStatus.JobRun triggering = getOrCreateTriggering(application, version, jobType.get());
                    application = application.withJobTriggering(jobType.get(),
                                                                application.deploying(),
                                                                triggering.at(),
                                                                version,
                                                                applicationVersion,
                                                                triggering.reason());
                }
            }

            // Update application with information from application package
            if (!options.deployCurrentVersion) {
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

            // Validate automated deployment
            if (!canDeployDirectlyTo(zone, options)) {
                if (!application.deploymentJobs().isDeployableTo(zone.environment(), application.deploying())) {
                    throw new IllegalArgumentException("Rejecting deployment of " + application + " to " + zone +
                                                       " as " + application.deploying() + " is not tested");
                }
                Deployment existingDeployment = application.deployments().get(zone);
                if (zone.environment().isProduction() && existingDeployment != null &&
                    existingDeployment.version().isAfter(version)) {
                    throw new IllegalArgumentException("Rejecting deployment of " + application + " to " + zone +
                                                       " as the requested version " + version + " is older than" +
                                                       " the current version " + existingDeployment.version());
                }
            }

            application = withRotation(application, zone);

            Set<String> rotationNames = new HashSet<>();
            Set<String> cnames = new HashSet<>();
            application.rotation().ifPresent(applicationRotation -> {
                rotationNames.add(applicationRotation.id().asString());
                cnames.add(applicationRotation.dnsName());
                cnames.add(applicationRotation.secureDnsName());
            });

            // Carry out deployment
            options = withVersion(version, options);
            ConfigServerClient.PreparedApplication preparedApplication =
                    configserverClient.prepare(new DeploymentId(applicationId, zone), options, cnames, rotationNames,
                                               applicationPackage.zippedContent());
            preparedApplication.activate();
            application = application.withNewDeployment(zone, applicationVersion, version, clock.instant());

            store(application);

            return new ActivateResult(new RevisionId(applicationPackage.hash()), preparedApplication.prepareResponse(),
                                      applicationPackage.zippedContent().length);
        }
    }

    /** Makes sure the application has a global rotation, if eligible. */
    private LockedApplication withRotation(LockedApplication application, ZoneId zone) {
        if (zone.environment() == Environment.prod && application.deploymentSpec().globalServiceId().isPresent()) {
            try (RotationLock rotationLock = rotationRepository.lock()) {
                Rotation rotation = rotationRepository.getRotation(application, rotationLock);
                application = application.with(rotation.id());
                store(application); // store assigned rotation even if deployment fails

                registerRotationInDns(rotation, application.rotation().get().dnsName());
                registerRotationInDns(rotation, application.rotation().get().secureDnsName());
            }
        }
        return application;
    }

    private ActivateResult unexpectedDeployment(ApplicationId applicationId, ZoneId zone,
                                                Optional<ApplicationPackage> applicationPackage) {
        Log logEntry = new Log();
        logEntry.level = "WARNING";
        logEntry.time = clock.instant().toEpochMilli();
        logEntry.message = "Ignoring deployment of " + get(applicationId) + " to " + zone + " as a deployment is not currently expected";
        PrepareResponse prepareResponse = new PrepareResponse();
        prepareResponse.log = Collections.singletonList(logEntry);
        prepareResponse.configChangeActions = new ConfigChangeActions(Collections.emptyList(), Collections.emptyList());
        return new ActivateResult(new RevisionId(applicationPackage.map(ApplicationPackage::hash)
                                                                   .orElse("0")), prepareResponse,
                                  applicationPackage.map(a -> a.zippedContent().length).orElse(0));
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
        for (DeploymentJobs.JobType job : application.deploymentJobs().jobStatus().keySet()) {
            Optional<ZoneId> zone = job.zone(controller.system());

            if ( ! job.isProduction() || (zone.isPresent() && application.deploymentSpec().includes(zone.get().environment(), zone.map(ZoneId::region))))
                continue;
            application = application.withoutDeploymentJob(job);
        }
        return application;
    }

    /**
     * Returns the existing triggering of the given type from this application,
     * or an incomplete one created in this method if none is present
     * This is needed (only) in the case where some external entity triggers a job.
     */
    private JobStatus.JobRun getOrCreateTriggering(Application application, Version version, DeploymentJobs.JobType jobType) {
        JobStatus status = application.deploymentJobs().jobStatus().get(jobType);
        if (status == null) return incompleteTriggeringEvent(version);
        if ( ! status.lastTriggered().isPresent()) return incompleteTriggeringEvent(version);
        return status.lastTriggered().get();
    }

    private JobStatus.JobRun incompleteTriggeringEvent(Version version) {
        return new JobStatus.JobRun(-1, version, ApplicationVersion.unknown, false, "", clock.instant());
    }

    private DeployOptions withVersion(Version version, DeployOptions options) {
        return new DeployOptions(options.screwdriverBuildJob,
                                 Optional.of(version),
                                 options.ignoreValidationErrors,
                                 options.deployCurrentVersion);
    }

    private ApplicationVersion toApplicationPackageRevision(ApplicationPackage applicationPackage,
                                                            Optional<ScrewdriverBuildJob> buildJob) {
        if ( ! buildJob.isPresent())
            return ApplicationVersion.from(applicationPackage.hash());

        GitRevision gitRevision = buildJob.get().gitRevision;
        if (gitRevision.repository == null || gitRevision.branch == null || gitRevision.commit == null)
            return ApplicationVersion.from(applicationPackage.hash());

        return ApplicationVersion.from(applicationPackage.hash(), new SourceRevision(gitRevision.repository.id(),
                                                                                     gitRevision.branch.id(),
                                                                                     gitRevision.commit.id()));
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

    /** Returns the endpoints of the deployment, or empty if obtaining them failed */
    public Optional<InstanceEndpoints> getDeploymentEndpoints(DeploymentId deploymentId) {
        try {
            List<RoutingEndpoint> endpoints = routingGenerator.endpoints(deploymentId);
            List<URI> endPointUrls = new ArrayList<>();
            for (RoutingEndpoint endpoint : endpoints) {
                try {
                    endPointUrls.add(new URI(endpoint.getEndpoint()));
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Routing generator returned illegal url's", e);
                }
            }
            return Optional.of(new InstanceEndpoints(endPointUrls));
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

            Tenant tenant = controller.tenants().tenant(new TenantId(id.tenant().value())).get();
            if (tenant.isAthensTenant() && ! token.isPresent())
                throw new IllegalArgumentException("Could not delete '" + application + "': No NToken provided");

            // Only delete in Athenz once
            if (id.instance().isDefault() && tenant.isAthensTenant()) {
                zmsClientFactory.createZmsClientWithAuthorizedServiceToken(token.get())
                                .deleteApplication(tenant.getAthensDomain().get(),
                                                   new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()));
            }
            db.deleteApplication(id);

            log.info("Deleted " + application);
        }));
    }

    /**
     * Replace any previous version of this application by this instance
     *
     * @param application a locked application to store
     */
    public void store(LockedApplication application) {
        db.store(application);
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

    public void notifyJobCompletion(JobReport report) {
        if ( ! get(report.applicationId()).isPresent()) {
            log.log(Level.WARNING, "Ignoring completion of job of project '" + report.projectId() +
                                   "': Unknown application '" + report.applicationId() + "'");
            return;
        }
        deploymentTrigger.triggerFromCompletion(report);
    }

    /**
     * Tells config server to schedule a restart of all nodes in this deployment
     *
     * @param hostname If non-empty, restart will only be scheduled for this host
     */
    public void restart(DeploymentId deploymentId, Optional<Hostname> hostname) {
        try {
            configserverClient.restart(deploymentId, hostname);
        }
        catch (NoInstanceException e) {
            throw new IllegalArgumentException("Could not restart " + deploymentId + ": No such deployment");
        }
    }

    /** Deactivate application in the given zone */
    public void deactivate(Application application, ZoneId zone) {
        deactivate(application, zone, Optional.empty(), false);
    }

    /** Deactivate a known deployment of the given application */
    public void deactivate(Application application, Deployment deployment, boolean requireThatDeploymentHasExpired) {
        deactivate(application, deployment.zone(), Optional.of(deployment), requireThatDeploymentHasExpired);
    }

    private void deactivate(Application application, ZoneId zone, Optional<Deployment> deployment,
                            boolean requireThatDeploymentHasExpired) {
        if (requireThatDeploymentHasExpired && deployment.isPresent()
            && ! DeploymentExpirer.hasExpired(controller.zoneRegistry(), deployment.get(), clock.instant()))
            return;

        lockOrThrow(application.id(), lockedApplication ->
                store(deactivate(lockedApplication, zone)));
    }

    /**
     * Deactivates a locked application without storing it
     *
     * @return the application with the deployment in the given zone removed
     */
    private LockedApplication deactivate(LockedApplication application, ZoneId zone) {
        try {
            configserverClient.deactivate(new DeploymentId(application.id(), zone));
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

    public ConfigServerClient configserverClient() { return configserverClient; }

    /**
     * Returns a lock which provides exclusive rights to changing this application.
     * Any operation which stores an application need to first acquire this lock, then read, modify
     * and store the application, and finally release (close) the lock.
     */
    Lock lock(ApplicationId application) {
        return curator.lock(application, Duration.ofMinutes(10));
    }

    /** Returns whether a direct deployment to given zone is allowed */
    private static boolean canDeployDirectlyTo(ZoneId zone, DeployOptions options) {
        return ! options.screwdriverBuildJob.isPresent() ||
               options.screwdriverBuildJob.get().screwdriverId == null ||
               zone.environment().isManuallyDeployed();
    }

    /** Verify that each of the production zones listed in the deployment spec exist in this system. */
    private void validate(DeploymentSpec deploymentSpec) {
        deploymentSpec.zones().stream()
                .filter(zone -> zone.environment() == Environment.prod)
                .forEach(zone -> {
                    if ( ! controller.zoneRegistry().hasZone(ZoneId.from(zone.environment(), zone.region().orElse(null))))
                        throw new IllegalArgumentException("Zone " + zone + " in deployment spec was not found in this system!");
                });
    }

    public RotationRepository rotationRepository() {
        return rotationRepository;
    }

}
