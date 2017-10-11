// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.athens.Athens;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerClient;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.github.GitHub;
import com.yahoo.vespa.hosted.controller.api.integration.jira.Jira;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.RotationRepository;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
    
    /** 
     * Status of Vespa versions across the system. 
     * This is expensive to maintain so that is done periodically by a maintenance job 
     */
    private final AtomicReference<VersionStatus> versionStatus;
    
    private final Clock clock;

    private final RotationRepository rotationRepository;
    private final GitHub gitHub;
    private final EntityService entityService;
    private final GlobalRoutingService globalRoutingService;
    private final ZoneRegistry zoneRegistry;
    private final ConfigServerClient configServerClient;
    private final MetricsService metricsService;
    private final Chef chefClient;
    private final Athens athens;
    private final ZmsClient zmsClient;

    /**
     * Creates a controller 
     * 
     * @param db the db storing persistent state
     * @param curator the curator instance storing working state shared between controller instances
     */
    @Inject
    public Controller(ControllerDb db, CuratorDb curator, RotationRepository rotationRepository,
                      GitHub gitHub, Jira jiraClient, EntityService entityService,
                      GlobalRoutingService globalRoutingService,
                      ZoneRegistry zoneRegistry, ConfigServerClient configServerClient,
                      MetricsService metricsService, NameService nameService,
                      RoutingGenerator routingGenerator, Chef chefClient, Athens athens) {
        this(db, curator, rotationRepository,
             gitHub, jiraClient, entityService, globalRoutingService, zoneRegistry,
             configServerClient, metricsService, nameService, routingGenerator, chefClient,
             Clock.systemUTC(), athens);
    }

    public Controller(ControllerDb db, CuratorDb curator, RotationRepository rotationRepository,
                      GitHub gitHub, Jira jiraClient, EntityService entityService,
                      GlobalRoutingService globalRoutingService,
                      ZoneRegistry zoneRegistry, ConfigServerClient configServerClient,
                      MetricsService metricsService, NameService nameService,
                      RoutingGenerator routingGenerator, Chef chefClient, Clock clock, Athens athens) {
        Objects.requireNonNull(db, "Controller db cannot be null");
        Objects.requireNonNull(curator, "Curator cannot be null");
        Objects.requireNonNull(rotationRepository, "Rotation repository cannot be null");
        Objects.requireNonNull(gitHub, "GitHubClient cannot be null");
        Objects.requireNonNull(jiraClient, "JiraClient cannot be null");
        Objects.requireNonNull(entityService, "EntityService cannot be null");
        Objects.requireNonNull(globalRoutingService, "GlobalRoutingService cannot be null");
        Objects.requireNonNull(zoneRegistry, "ZoneRegistry cannot be null");
        Objects.requireNonNull(configServerClient, "ConfigServerClient cannot be null");
        Objects.requireNonNull(metricsService, "MetricsService cannot be null");
        Objects.requireNonNull(nameService, "NameService cannot be null");
        Objects.requireNonNull(routingGenerator, "RoutingGenerator cannot be null");
        Objects.requireNonNull(chefClient, "ChefClient cannot be null");
        Objects.requireNonNull(clock, "Clock cannot be null");
        Objects.requireNonNull(athens, "Athens cannot be null");

        this.rotationRepository = rotationRepository;
        this.curator = curator;
        this.gitHub = gitHub;
        this.entityService = entityService;
        this.globalRoutingService = globalRoutingService;
        this.zoneRegistry = zoneRegistry;
        this.configServerClient = configServerClient;
        this.metricsService = metricsService;
        this.chefClient = chefClient;
        this.clock = clock;
        this.athens = athens;
        this.zmsClient = athens.zmsClientFactory().createClientWithServicePrincipal();

        applicationController = new ApplicationController(this, db, curator, rotationRepository, athens.zmsClientFactory(),
                                                          nameService, configServerClient, routingGenerator, clock);
        tenantController = new TenantController(this, db, curator, entityService);
        versionStatus = new AtomicReference<>(VersionStatus.empty());
    }
    
    /** Returns the instance controlling tenants */
    public TenantController tenants() { return tenantController; }

    /** Returns the instance controlling applications */
    public ApplicationController applications() { return applicationController; }

    public List<AthensDomain> getDomainList(String prefix) {
        return zmsClient.getDomainList(prefix);
    }

    public Athens athens() {
        return athens;
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

    public URI getElkUri(Environment environment, RegionName region, DeploymentId deploymentId) {
        return elkUrl(zoneRegistry.getLogServerUri(environment, region), deploymentId);
    }

    public List<URI> getConfigServerUris(Environment environment, RegionName region) {
        return zoneRegistry.getConfigServerUris(environment, region);
    }
    
    public ZoneRegistry zoneRegistry() { return zoneRegistry; }

    private URI elkUrl(Optional<URI> kibanaHost, DeploymentId deploymentId) {
        String kibanaQuery = "/#/discover?_g=()&_a=(columns:!(_source)," +
                             "index:'logstash-*',interval:auto," +
                             "query:(query_string:(analyze_wildcard:!t,query:'" +
                             "HV-tenant:%22" + deploymentId.applicationId().tenant().value() + "%22%20" +
                             "AND%20HV-application:%22" + deploymentId.applicationId().application().value() + "%22%20" +
                             "AND%20HV-region:%22" + deploymentId.zone().region().value() + "%22%20" +
                             "AND%20HV-instance:%22" + deploymentId.applicationId().instance().value() + "%22%20" +
                             "AND%20HV-environment:%22" + deploymentId.zone().environment().value() + "%22'))," +
                             "sort:!('@timestamp',desc))";

        URI kibanaPath = URI.create(kibanaQuery);
        if (kibanaHost.isPresent()) {
            return kibanaHost.get().resolve(kibanaPath);
        } else {
            return null;
        }
    }

    public Set<URI> getRotationUris(ApplicationId id) {
        return rotationRepository.getRotationUris(id);
    }

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
    // TODO: What is this
    public JsonNode grabLog(DeploymentId deploymentId) {
        return configServerClient.grabLog(deploymentId);
    }

    public GitHub gitHub() { return gitHub; }

    /** Replace the current version status by a new one */
    public void updateVersionStatus(VersionStatus newStatus) {
        VersionStatus currentStatus = versionStatus();
        if (newStatus.systemVersion().isPresent() &&
            ! newStatus.systemVersion().equals(currentStatus.systemVersion())) {
            log.info("Changing system version from " + printableVersion(currentStatus.systemVersion()) +
                     " to " + printableVersion(newStatus.systemVersion()));
            curator.writeSystemVersion(newStatus.systemVersion().get().versionNumber());
        }

        this.versionStatus.set(newStatus); 
    }
    
    /** Returns the latest known version status. Calling this is free but the status may be slightly out of date. */
    public VersionStatus versionStatus() { return versionStatus.get(); }
    
    /** Returns the current system version: The controller should drive towards running all applications on this version */
    public Version systemVersion() { return curator.readSystemVersion(); }

    public MetricsService metricsService() { return metricsService; }

    public SystemName system() {
        return zoneRegistry.system();
    }

    public Chef chefClient() {
        return chefClient;
    }

    private String printableVersion(Optional<VespaVersion> vespaVersion) {
        return vespaVersion.map(v -> v.versionNumber().toFullString()).orElse("Unknown");
    }

}
