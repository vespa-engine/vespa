// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.GitRevision;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.athens.mock.AthensDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.athens.mock.AthensMock;
import com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService;
import com.yahoo.vespa.hosted.controller.api.integration.github.GitHubMock;
import com.yahoo.vespa.hosted.controller.api.integration.jira.JiraMock;
import com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.integration.MockMetricsService;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MemoryControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.routing.MockRoutingGenerator;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.rotation.MemoryRotationRepository;

import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Convenience methods for controller tests.
 * This completely wraps TestEnvironment to make it easier to get rid of that in the future.
 * 
 * @author bratseth
 */
public final class ControllerTester {

    private final ControllerDb db = new MemoryControllerDb();
    private final AthensDbMock athensDb = new AthensDbMock();
    private final ManualClock clock = new ManualClock();
    private final ConfigServerClientMock configServerClientMock = new ConfigServerClientMock();
    private final ZoneRegistryMock zoneRegistryMock = new ZoneRegistryMock();
    private final GitHubMock gitHubMock = new GitHubMock();
    private final CuratorDb curator = new MockCuratorDb();
    private final MemoryNameService memoryNameService = new MemoryNameService();
    private Controller controller = createController(db, curator, configServerClientMock, clock, gitHubMock,
                                                     zoneRegistryMock, athensDb, memoryNameService);
    
    private static final Controller createController(ControllerDb db, CuratorDb curator,
                                                     ConfigServerClientMock configServerClientMock, ManualClock clock,
                                                     GitHubMock gitHubClientMock, ZoneRegistryMock zoneRegistryMock,
                                                     AthensDbMock athensDb, MemoryNameService nameService) {
        Controller controller = new Controller(db,
                                               curator,
                                               new MemoryRotationRepository(),
                                               gitHubClientMock,
                                               new JiraMock(),
                                               new MemoryEntityService(),
                                               new MemoryGlobalRoutingService(),
                                               zoneRegistryMock,
                                               configServerClientMock,
                                               new MockMetricsService(),
                                               nameService,
                                               new MockRoutingGenerator(),
                                               new ChefMock(),
                                               clock,
                                               new AthensMock(athensDb));
        controller.updateVersionStatus(VersionStatus.compute(controller));
        return controller;
    }

    public Controller controller() { return controller; }
    public CuratorDb curator() { return curator; }
    public ManualClock clock() { return clock; }
    public AthensDbMock athensDb() { return athensDb; }
    public MemoryNameService nameService() { return memoryNameService; }

    /** Create a new controller instance. Useful to verify that controller state is rebuilt from persistence */
    public final void createNewController() {
        controller = createController(db, curator, configServerClientMock, clock, gitHubMock, zoneRegistryMock,
                                      athensDb, memoryNameService);
    }

    public ZoneRegistryMock getZoneRegistryMock() { return zoneRegistryMock; }

    public ConfigServerClientMock configServerClientMock() { return configServerClientMock; }

    public GitHubMock gitHubClientMock () { return gitHubMock; }

    /** Set the application with the given id to currently be in the progress of rolling out the given change */
    public void setDeploying(ApplicationId id, Optional<Change> change) {
        try (Lock lock = controller.applications().lock(id)) {
            controller.applications().store(controller.applications().require(id).withDeploying(change), lock);
        }
    }
    
    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, Environment environment, long projectId, Long propertyId) {
        return createAndDeploy(tenantName, domainName, applicationName, toZone(environment), projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, 
                                       String instanceName, Zone zone, long projectId, Long propertyId) {
        TenantId tenant = createTenant(tenantName, domainName, propertyId);
        Application application = createApplication(tenant, applicationName, instanceName, projectId);
        deploy(application, zone);
        return application;
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName,
                                       String instanceName, Environment environment, long projectId, Long propertyId) {
        return createAndDeploy(tenantName, domainName, applicationName, instanceName, toZone(environment), projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, Zone zone, long projectId, Long propertyId) {
        return createAndDeploy(tenantName, domainName, applicationName, "default", zone, projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, Environment environment, long projectId) {
        return createAndDeploy(tenantName, domainName, applicationName, environment, projectId, null);
    }
    
    public Zone toZone(Environment environment) {
        switch (environment) {
            case dev: case test: return new Zone(environment, RegionName.from("us-east-1"));
            case staging: return new Zone(environment, RegionName.from("us-east-3"));
            default: return new Zone(environment, RegionName.from("us-west-1"));
        }
    }

    public AthensDomain createDomain(String domainName) {
        AthensDomain domain = new AthensDomain(domainName);
        athensDb.addDomain(new AthensDbMock.Domain(domain));
        return domain;
    }
    
    public TenantId createTenant(String tenantName, String domainName, Long propertyId) {
        TenantId id = new TenantId(tenantName);
        Optional<Tenant> existing = controller().tenants().tenant(id);
        if (existing.isPresent()) return id;

        Tenant tenant = Tenant.createAthensTenant(id, createDomain(domainName), new Property("app1Property"),
                propertyId == null ? Optional.empty() : Optional.of(new PropertyId(propertyId.toString())));
        controller().tenants().addTenant(tenant, Optional.of(TestIdentities.userNToken));
        assertNotNull(controller().tenants().tenant(id));
        return id;
    }
    
    public Application createApplication(TenantId tenant, String applicationName, String instanceName, long projectId) {
        ApplicationId applicationId = applicationId(tenant.id(), applicationName, instanceName);
        Application application = controller().applications().createApplication(applicationId, Optional.of(TestIdentities.userNToken))
                                                             .withProjectId(projectId);
        assertTrue(controller().applications().get(applicationId).isPresent());
        return application;
    }

    public void deploy(Application application, Zone zone) {
        deploy(application, zone, new ApplicationPackage(new byte[0]));
    }

    public void deploy(Application application, Zone zone, ApplicationPackage applicationPackage) {
        deploy(application, zone, applicationPackage, false);
    }

    public void deploy(Application application, Zone zone, ApplicationPackage applicationPackage, boolean deployCurrentVersion) {
        ScrewdriverId app1ScrewdriverId = new ScrewdriverId(String.valueOf(application.deploymentJobs().projectId().get()));
        GitRevision app1RevisionId = new GitRevision(new GitRepository("repo"), new GitBranch("master"), new GitCommit("commit1"));
        controller().applications().deployApplication(application.id(),
                                                      zone,
                                                      applicationPackage,
                                                      new DeployOptions(Optional.of(new ScrewdriverBuildJob(app1ScrewdriverId, app1RevisionId)), Optional.empty(), false, deployCurrentVersion));
    }

    public ApplicationId applicationId(String tenant, String application, String instance) {
        return ApplicationId.from(TenantName.from(tenant),
                                  ApplicationName.from(application),
                                  InstanceName.from(instance));
    }

}
