// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.ApplicationAlias;
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
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerClient;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NoInstanceException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.DeploymentExpirer;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.rotation.RotationRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private final RotationRepository rotationRepository;
    private final AthenzClientFactory zmsClientFactory;
    private final NameService nameService;
    private final ConfigServerClient configserverClient;
    private final RoutingGenerator routingGenerator;
    private final Clock clock;

    private final DeploymentTrigger deploymentTrigger;
    
    ApplicationController(Controller controller, ControllerDb db, CuratorDb curator,
                          RotationRepository rotationRepository,
                          AthenzClientFactory zmsClientFactory,
                          NameService nameService, ConfigServerClient configserverClient,
                          RoutingGenerator routingGenerator, Clock clock) {
        this.controller = controller;
        this.db = db;
        this.curator = curator;
        this.rotationRepository = rotationRepository;
        this.zmsClientFactory = zmsClientFactory;
        this.nameService = nameService;
        this.configserverClient = configserverClient;
        this.routingGenerator = routingGenerator;
        this.clock = clock;

        this.deploymentTrigger = new DeploymentTrigger(controller, curator, clock);

        for (Application application : db.listApplications()) {
            try (Lock lock = lock(application.id())) {
                Optional<Application> optionalApplication = db.getApplication(application.id()); // re-get inside lock
                if ( ! optionalApplication.isPresent()) continue; // was removed since listing; ok
                store(optionalApplication.get(), lock); // re-write all applications to update storage format
            }
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
        if ( ! (id.instance().value().equals("default") || id.instance().value().startsWith("default-pr"))) // TODO: Support instances properly
            throw new UnsupportedOperationException("Only the instance names 'default' and names starting with 'default-pr' are supported at the moment");
        try (Lock lock = lock(id)) {
            if (get(id).isPresent())
                throw new IllegalArgumentException("An application with id '" + id + "' already exists");

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
            Application application = new Application(id);
            store(application, lock);
            log.info("Created " + application);
            return application;
        }
    }

    /** Deploys an application. If the application does not exist it is created. */
    // TODO: Get rid of the options arg
    public ActivateResult deployApplication(ApplicationId applicationId, Zone zone,
                                            ApplicationPackage applicationPackage, DeployOptions options) {
        try (Lock lock = lock(applicationId)) {
            // Determine what we are doing
            Application application = get(applicationId).orElse(new Application(applicationId));

            Version version;
            if (options.deployCurrentVersion)
                version = application.currentVersion(controller, zone);
            else if (canDeployDirectlyTo(zone, options))
                version = options.vespaVersion.map(Version::new).orElse(controller.systemVersion());
            else if ( ! application.deploying().isPresent() && ! zone.environment().isManuallyDeployed())
                return unexpectedDeployment(applicationId, zone, applicationPackage);
            else
                version = application.currentDeployVersion(controller, zone);

            DeploymentJobs.JobType jobType = DeploymentJobs.JobType.from(controller.zoneRegistry().system(), zone);
            ApplicationRevision revision = toApplicationPackageRevision(applicationPackage, options.screwdriverBuildJob);

            if ( ! options.deployCurrentVersion) {
                // Add missing information to application (unless we're deploying the previous version (initial staging step)
                application = application.with(applicationPackage.deploymentSpec());
                application = application.with(applicationPackage.validationOverrides());
                if (options.screwdriverBuildJob.isPresent() && options.screwdriverBuildJob.get().screwdriverId != null)
                    application = application.withProjectId(options.screwdriverBuildJob.get().screwdriverId.value());
                if (application.deploying().isPresent() && application.deploying().get() instanceof Change.ApplicationChange)
                    application = application.withDeploying(Optional.of(Change.ApplicationChange.of(revision)));
                if ( ! triggeredWith(revision, application, jobType) && !canDeployDirectlyTo(zone, options) && jobType != null) {
                    // Triggering information is used to store which changes were made or attempted
                    // - For all applications, we don't have complete control over which revision is actually built,
                    //   so we update it here with what we actually triggered if necessary
                    application = application.with(application.deploymentJobs()
                                                           .withTriggering(jobType, application.deploying(),
                                                                           version, Optional.of(revision),
                                                                           clock.instant()));
                }

                // Delete zones not listed in DeploymentSpec, if allowed
                // We do this at deployment time to be able to return a validation failure message when necessary
                application = deleteRemovedDeployments(application);

                // Clean up deployment jobs that are no longer referenced by deployment spec
                application = deleteUnreferencedDeploymentJobs(application);

                store(application, lock); // store missing information even if we fail deployment below
            }

            // Ensure that the deploying change is tested
            if (! canDeployDirectlyTo(zone, options) &&
                ! application.deploymentJobs().isDeployableTo(zone.environment(), application.deploying()))
                throw new IllegalArgumentException("Rejecting deployment of " + application + " to " + zone +
                                                   " as " + application.deploying().get() + " is not tested");

            // Carry out deployment
            DeploymentId deploymentId = new DeploymentId(applicationId, zone);
            ApplicationRotation rotationInDns = registerRotationInDns(deploymentId, getOrAssignRotation(deploymentId, 
                                                                                                        applicationPackage));
            options = withVersion(version, options);            
            ConfigServerClient.PreparedApplication preparedApplication = 
                    configserverClient.prepare(deploymentId, options, rotationInDns.cnames(), rotationInDns.rotations(), 
                                               applicationPackage.zippedContent());
            preparedApplication.activate();

            // Use info from previous deployments is available
            Deployment previousDeployment = application.deployments().getOrDefault(zone, new Deployment(zone, revision, version, clock.instant()));
            Deployment newDeployment = new Deployment(zone, revision, version, clock.instant(),
                        previousDeployment.clusterUtils(), previousDeployment.clusterInfo(), previousDeployment.metrics());

            application = application.with(newDeployment);
            store(application, lock);

            return new ActivateResult(new RevisionId(applicationPackage.hash()), preparedApplication.prepareResponse());
        }
    }

    private ActivateResult unexpectedDeployment(ApplicationId applicationId, Zone zone, ApplicationPackage applicationPackage) {
        Log logEntry = new Log();
        logEntry.level = "WARNING";
        logEntry.time = clock.instant().toEpochMilli();
        logEntry.message = "Ignoring deployment of " + get(applicationId) + " to " + zone + " as a deployment is not currently expected";
        PrepareResponse prepareResponse = new PrepareResponse();
        prepareResponse.log = Collections.singletonList(logEntry);
        prepareResponse.configChangeActions = new ConfigChangeActions(Collections.emptyList(), Collections.emptyList());
        return new ActivateResult(new RevisionId(applicationPackage.hash()), prepareResponse);
    }

    private Application deleteRemovedDeployments(Application application) {
        List<Deployment> deploymentsToRemove = application.deployments().values().stream()
                .filter(deployment -> deployment.zone().environment() == Environment.prod)
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
        
        Application applicationWithRemoval = application;
        for (Deployment deployment : deploymentsToRemove)
            applicationWithRemoval = deactivate(applicationWithRemoval, deployment, false);
        return applicationWithRemoval;
    }

    private Application deleteUnreferencedDeploymentJobs(Application application) {
        for (DeploymentJobs.JobType job : application.deploymentJobs().jobStatus().keySet()) {
            Optional<Zone> zone = job.zone(controller.system());
            if ( ! job.isProduction() || (zone.isPresent() && application.deploymentSpec().includes(zone.get().environment(), zone.map(Zone::region))))
                continue;
            application = application.withoutDeploymentJob(job);
        }
        return application;
    }

    private boolean triggeredWith(ApplicationRevision revision, Application application, DeploymentJobs.JobType jobType) {
        if (jobType == null) return false;
        JobStatus status = application.deploymentJobs().jobStatus().get(jobType);
        if (status == null) return false;
        if ( ! status.lastTriggered().isPresent()) return false;
        JobStatus.JobRun triggered = status.lastTriggered().get();
        if ( ! triggered.revision().isPresent()) return false;
        return triggered.revision().get().equals(revision);
    }
    
    private DeployOptions withVersion(Version version, DeployOptions options) {
        return new DeployOptions(options.screwdriverBuildJob, 
                                 Optional.of(version), 
                                 options.ignoreValidationErrors, 
                                 options.deployCurrentVersion);
    }

    private ApplicationRevision toApplicationPackageRevision(ApplicationPackage applicationPackage,
                                                             Optional<ScrewdriverBuildJob> screwDriverBuildJob) {
        if ( ! screwDriverBuildJob.isPresent())
            return ApplicationRevision.from(applicationPackage.hash());
        
        GitRevision gitRevision = screwDriverBuildJob.get().gitRevision;
        if (gitRevision.repository == null || gitRevision.branch == null || gitRevision.commit == null)
            return ApplicationRevision.from(applicationPackage.hash());

        return ApplicationRevision.from(applicationPackage.hash(), new SourceRevision(gitRevision.repository.id(),
                                                                                      gitRevision.branch.id(),
                                                                                      gitRevision.commit.id()));
    }

    private ApplicationRotation registerRotationInDns(DeploymentId deploymentId, ApplicationRotation applicationRotation) {
        ApplicationAlias alias = new ApplicationAlias(deploymentId.applicationId());
        if (applicationRotation.rotations().isEmpty()) return applicationRotation;

        Rotation rotation = applicationRotation.rotations().iterator().next(); // at this time there should be only one rotation assigned
        String endpointName = alias.toString();
        try {
            Optional<Record> record = nameService.findRecord(Record.Type.CNAME, endpointName);
            if (!record.isPresent()) {
                RecordId recordId = nameService.createCname(endpointName, rotation.rotationName);
                log.info("Registered mapping with record ID " + recordId.id() + ": " +
                                 endpointName + " -> " + rotation.rotationName);
            }
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to register CNAME", e);
        }
        return new ApplicationRotation(Collections.singleton(endpointName), Collections.singleton(rotation));
    }

    private ApplicationRotation getOrAssignRotation(DeploymentId deploymentId, ApplicationPackage applicationPackage) {
        if (deploymentId.zone().environment().equals(Environment.prod)) {
            return new ApplicationRotation(Collections.emptySet(), 
                                           rotationRepository.getOrAssignRotation(deploymentId.applicationId(), 
                                                                                  applicationPackage.deploymentSpec()));
        } else {
            return new ApplicationRotation(Collections.emptySet(), 
                                           Collections.emptySet());
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
            log.log(Level.WARNING, "Failed to get endpoint information for " + deploymentId, e);
            return Optional.empty();
        }
    }

    /**
     * Deletes the application with this id
     * 
     * @return the deleted application, or null if it did not exist
     * @throws IllegalArgumentException if the application has deployments or the caller is not authorized
     */
    public Application deleteApplication(ApplicationId id, Optional<NToken> token) {
        try (Lock lock = lock(id)) {
            Optional<Application> application = get(id);
            if ( ! application.isPresent()) return null;
            if ( ! application.get().deployments().isEmpty())
                throw new IllegalArgumentException("Could not delete '" + application + "': It has active deployments");
            
            Tenant tenant = controller.tenants().tenant(new TenantId(id.tenant().value())).get();
            if (tenant.isAthensTenant() && ! token.isPresent())
                throw new IllegalArgumentException("Could not delete '" + application + "': No NToken provided");

            // NB: Next 2 lines should have been one transaction
            if (tenant.isAthensTenant())
                zmsClientFactory.createZmsClientWithAuthorizedServiceToken(token.get())
                        .deleteApplication(tenant.getAthensDomain().get(), new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()));
            db.deleteApplication(id);

            log.info("Deleted " + application.get());
            return application.get();
        }
    }

    public void setJiraIssueId(ApplicationId id, Optional<String> jiraIssueId) {
        try (Lock lock = lock(id)) {
            get(id).ifPresent(application -> store(application.withJiraIssueId(jiraIssueId), lock));
        }
    }

    /** 
     * Replace any previous version of this application by this instance 
     * 
     * @param application the application version to store
     * @param lock the lock held on this application since before modification started
     */
    @SuppressWarnings("unused") // lock is part of the signature to remind people to acquire it, not needed internally
    public void store(Application application, Lock lock) {
        db.store(application);
    }

    public void notifyJobCompletion(JobReport report) {
        if ( ! get(report.applicationId()).isPresent()) {
            log.log(Level.WARNING, "Ignoring completion of job of project '" + report.projectId() + 
                                   "': Unknown application '" + report.applicationId() + "'");
            return;
        }
        deploymentTrigger.triggerFromCompletion(report);
    }

    // TODO: Collapse this method and the next
    public void restart(DeploymentId deploymentId) {
        try {
            configserverClient.restart(deploymentId, Optional.empty());
        }
        catch (NoInstanceException e) {
            throw new IllegalArgumentException("Could not restart " + deploymentId + ": No such deployment");
        }
    }
    public void restartHost(DeploymentId deploymentId, Hostname hostname) {
        try {
            configserverClient.restart(deploymentId, Optional.of(hostname));
        }
        catch (NoInstanceException e) {
            throw new IllegalArgumentException("Could not restart " + deploymentId + ": No such deployment");
        }
    }

    /** Deactivate application in the given zone */
    public Application deactivate(Application application, Zone zone) {
        return deactivate(application, zone, Optional.empty(), false);
    }

    /** Deactivate a known deployment of the given application */
    public Application deactivate(Application application, Deployment deployment, boolean requireThatDeploymentHasExpired) {
        return deactivate(application, deployment.zone(), Optional.of(deployment), requireThatDeploymentHasExpired);
    }

    private Application deactivate(Application application, Zone zone, Optional<Deployment> deployment,
                                   boolean requireThatDeploymentHasExpired) {
        try (Lock lock = lock(application.id())) {
            if (deployment.isPresent() && requireThatDeploymentHasExpired && ! DeploymentExpirer.hasExpired(
                    controller.zoneRegistry(), deployment.get(), clock.instant())) {
                return application;
            }

            try { 
                configserverClient.deactivate(new DeploymentId(application.id(), zone));
            }  catch (NoInstanceException ignored) {
                // ok; already gone
            }
            application = application.withoutDeploymentIn(zone);
            store(application, lock);
            return application;
        }
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
    public Lock lock(ApplicationId application) {
        return curator.lock(application, Duration.ofMinutes(10));
    }

    /** Returns whether a direct deployment to given zone is allowed */
    private static boolean canDeployDirectlyTo(Zone zone, DeployOptions options) {
        return !options.screwdriverBuildJob.isPresent() ||
               options.screwdriverBuildJob.get().screwdriverId == null ||
               zone.environment().isManuallyDeployed();
    }

    private static final class ApplicationRotation {
        
        private final ImmutableSet<String> cnames;
        private final ImmutableSet<Rotation> rotations;
        
        public ApplicationRotation(Set<String> cnames, Set<Rotation> rotations) {
            this.cnames = ImmutableSet.copyOf(cnames);
            this.rotations = ImmutableSet.copyOf(rotations);
        }
        
        public Set<String> cnames() { return cnames; }
        public Set<Rotation> rotations() { return rotations; }
        
    }
    
}
