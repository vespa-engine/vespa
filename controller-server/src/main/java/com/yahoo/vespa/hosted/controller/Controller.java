// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerClient;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.github.GitHub;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Organization;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * API to the controller. This contains (currently: should contain) the object model of everything the 
 * controller cares about, mainly tenants and applications.
 * 
 * As the controller runtime and Controller object are singletons, this instance can read from the object model
 * in memory. However, all changes to the object model must be persisted in the controller db.
 * 
 * All the individual model objects reachable from the Controller are immutable.
 * 
 * Access to the controller is multithread safe, provided the locking methods are
 * used when accessing, modifying and storing objects provided by the controller.
 * 
 * @author bratseth
 */
public class Controller extends AbstractComponent {

    private static final Logger log = Logger.getLogger(Controller.class.getName());
    
    private final CuratorDb curator;
    private final ApplicationController applicationController;
    private final TenantController tenantController;
    private final Clock clock;
    private final GitHub gitHub;
    private final EntityService entityService;
    private final GlobalRoutingService globalRoutingService;
    private final ZoneRegistry zoneRegistry;
    private final ConfigServerClient configServerClient;
    private final NodeRepositoryClientInterface nodeRepositoryClient;
    private final MetricsService metricsService;
    private final Chef chefClient;
    private final Organization organization;
    private final AthenzClientFactory athenzClientFactory;

    /**
     * Creates a controller 
     * 
     * @param db the db storing persistent state
     * @param curator the curator instance storing working state shared between controller instances
     */
    @Inject
    public Controller(ControllerDb db, CuratorDb curator, RotationsConfig rotationsConfig,
                      GitHub gitHub, EntityService entityService, Organization organization,
                      GlobalRoutingService globalRoutingService,
                      ZoneRegistry zoneRegistry, ConfigServerClient configServerClient, NodeRepositoryClientInterface nodeRepositoryClient,
                      MetricsService metricsService, NameService nameService,
                      RoutingGenerator routingGenerator, Chef chefClient, AthenzClientFactory athenzClientFactory) {
        this(db, curator, rotationsConfig,
             gitHub, entityService, organization, globalRoutingService, zoneRegistry,
             configServerClient, nodeRepositoryClient, metricsService, nameService, routingGenerator, chefClient,
             Clock.systemUTC(), athenzClientFactory);
    }

    public Controller(ControllerDb db, CuratorDb curator, RotationsConfig rotationsConfig,
                      GitHub gitHub, EntityService entityService, Organization organization,
                      GlobalRoutingService globalRoutingService,
                      ZoneRegistry zoneRegistry, ConfigServerClient configServerClient, NodeRepositoryClientInterface nodeRepositoryClient,
                      MetricsService metricsService, NameService nameService,
                      RoutingGenerator routingGenerator, Chef chefClient, Clock clock,
                      AthenzClientFactory athenzClientFactory) {
        Objects.requireNonNull(db, "Controller db cannot be null");
        Objects.requireNonNull(curator, "Curator cannot be null");
        Objects.requireNonNull(rotationsConfig, "RotationsConfig cannot be null");
        Objects.requireNonNull(gitHub, "GitHubClient cannot be null");
        Objects.requireNonNull(entityService, "EntityService cannot be null");
        Objects.requireNonNull(organization, "Organization cannot be null");
        Objects.requireNonNull(globalRoutingService, "GlobalRoutingService cannot be null");
        Objects.requireNonNull(zoneRegistry, "ZoneRegistry cannot be null");
        Objects.requireNonNull(configServerClient, "ConfigServerClient cannot be null");
        Objects.requireNonNull(nodeRepositoryClient, "NodeRepositoryClientInterface cannot be null");
        Objects.requireNonNull(metricsService, "MetricsService cannot be null");
        Objects.requireNonNull(nameService, "NameService cannot be null");
        Objects.requireNonNull(routingGenerator, "RoutingGenerator cannot be null");
        Objects.requireNonNull(chefClient, "ChefClient cannot be null");
        Objects.requireNonNull(clock, "Clock cannot be null");
        Objects.requireNonNull(athenzClientFactory, "Athens cannot be null");

        this.curator = curator;
        this.gitHub = gitHub;
        this.entityService = entityService;
        this.organization = organization;
        this.globalRoutingService = globalRoutingService;
        this.zoneRegistry = zoneRegistry;
        this.configServerClient = configServerClient;
        this.nodeRepositoryClient = nodeRepositoryClient;
        this.metricsService = metricsService;
        this.chefClient = chefClient;
        this.clock = clock;
        this.athenzClientFactory = athenzClientFactory;

        applicationController = new ApplicationController(this, db, curator, athenzClientFactory,
                                                          rotationsConfig,
                                                          nameService, configServerClient, routingGenerator, clock);
        tenantController = new TenantController(this, db, curator, entityService, athenzClientFactory);
    }
    
    /** Returns the instance controlling tenants */
    public TenantController tenants() { return tenantController; }

    /** Returns the instance controlling applications */
    public ApplicationController applications() { return applicationController; }

    public List<AthenzDomain> getDomainList(String prefix) {
        return athenzClientFactory.createZmsClientWithServicePrincipal().getDomainList(prefix);
    }

    /**
     * Fetch list of all active OpsDB properties.
     *
     * @return Hashed map with the property ID as key and property name as value
     */
    public Map<PropertyId, Property> fetchPropertyList() {
        return entityService.listProperties();
    }

    public Clock clock() { return clock; }

    public Optional<URI> getLogServerUrl(DeploymentId deploymentId) {
        return zoneRegistry.getLogServerUri(deploymentId);
    }

    // TODO Rename to getConfigServerUris once port 4080 is removed from configservers
    public List<URI> getSecureConfigServerUris(ZoneId zoneId) {
        return zoneRegistry.getConfigServerSecureUris(zoneId);
    }
    
    public ZoneRegistry zoneRegistry() { return zoneRegistry; }

    public Map<String, RotationStatus> getHealthStatus(String hostname) {
        return globalRoutingService.getHealthStatus(hostname);
    }

    // TODO: Model the response properly
    public JsonNode waitForConfigConvergence(DeploymentId deploymentId, long timeout) {
        return configServerClient.waitForConfigConverge(deploymentId, timeout);
    }

    public ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName, String environment, String region) {
        return configServerClient.getApplicationView(tenantName, applicationName, instanceName, environment, region);
    }

    // TODO: Model the response properly
    public Map<?,?> getServiceApiResponse(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String restPath) {
        return configServerClient.getServiceApiResponse(tenantName, applicationName, instanceName, environment, region, serviceName, restPath);
    }

    // TODO: Model the response properly
    // TODO: What is this -- I believe it fetches, and purges, errors from some log server
    public JsonNode grabLog(DeploymentId deploymentId) {
        return configServerClient.grabLog(deploymentId);
    }

    public GitHub gitHub() {
        return gitHub;
    }

    /** Replace the current version status by a new one */
    public void updateVersionStatus(VersionStatus newStatus) {
        VersionStatus currentStatus = versionStatus();
        if (newStatus.systemVersion().isPresent() &&
            ! newStatus.systemVersion().equals(currentStatus.systemVersion())) {
            log.info("Changing system version from " + printableVersion(currentStatus.systemVersion()) +
                     " to " + printableVersion(newStatus.systemVersion()));
        }
        curator.writeVersionStatus(newStatus);
    }
    
    /** Returns the latest known version status. Calling this is free but the status may be slightly out of date. */
    public VersionStatus versionStatus() { return curator.readVersionStatus(); }
    
    /** Returns the current system version: The controller should drive towards running all applications on this version */
    public Version systemVersion() {
        return versionStatus().systemVersion()
                .map(VespaVersion::versionNumber)
                .orElse(Vtag.currentVersion);
    }

    public MetricsService metricsService() {
        return metricsService;
    }

    public SystemName system() {
        return zoneRegistry.system();
    }

    public Chef chefClient() {
        return chefClient;
    }

    public Organization organization() {
        return organization;
    }

    public CuratorDb curator() {
        return curator;
    }

    public NodeRepositoryClientInterface nodeRepositoryClient() {
        return nodeRepositoryClient;
    }

    private String printableVersion(Optional<VespaVersion> vespaVersion) {
        return vespaVersion.map(v -> v.versionNumber().toFullString()).orElse("Unknown");
    }

}
