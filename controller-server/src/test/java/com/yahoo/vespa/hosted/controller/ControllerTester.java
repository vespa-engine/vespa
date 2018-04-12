// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Slime;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.GitRevision;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService;
import com.yahoo.vespa.hosted.controller.api.integration.github.GitHubMock;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockOrganization;
import com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.deployment.MockBuildService;
import com.yahoo.vespa.hosted.controller.integration.MockMetricsService;
import com.yahoo.vespa.hosted.controller.persistence.ApplicationSerializer;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MemoryControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.routing.MockRoutingGenerator;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

/**
 * Convenience methods for controller tests.
 *
 * @author bratseth
 * @author mpolden
 */
public final class ControllerTester {

    private final ControllerDb db;
    private final AthenzDbMock athenzDb;
    private final ManualClock clock;
    private final ConfigServerClientMock configServer;
    private final ZoneRegistryMock zoneRegistry;
    private final GitHubMock gitHub;
    private final CuratorDb curator;
    private final MemoryNameService nameService;
    private final RotationsConfig rotationsConfig;
    private final ArtifactRepositoryMock artifactRepository;
    private final EntityService entityService;
    private final MockBuildService buildService;

    private Controller controller;

    public ControllerTester() {
        this(new MemoryControllerDb(), new AthenzDbMock(), new ManualClock(), new ConfigServerClientMock(),
             new ZoneRegistryMock(), new GitHubMock(), new MockCuratorDb(), defaultRotationsConfig(),
             new MemoryNameService(), new ArtifactRepositoryMock(), new MemoryEntityService(), new MockBuildService());
    }

    public ControllerTester(ManualClock clock) {
        this(new MemoryControllerDb(), new AthenzDbMock(), clock, new ConfigServerClientMock(),
             new ZoneRegistryMock(), new GitHubMock(), new MockCuratorDb(), defaultRotationsConfig(),
             new MemoryNameService(), new ArtifactRepositoryMock(), new MemoryEntityService(), new MockBuildService());
    }

    public ControllerTester(RotationsConfig rotationsConfig) {
        this(new MemoryControllerDb(), new AthenzDbMock(), new ManualClock(), new ConfigServerClientMock(),
             new ZoneRegistryMock(), new GitHubMock(), new MockCuratorDb(), rotationsConfig, new MemoryNameService(),
             new ArtifactRepositoryMock(), new MemoryEntityService(), new MockBuildService());
    }

    private ControllerTester(ControllerDb db, AthenzDbMock athenzDb, ManualClock clock,
                             ConfigServerClientMock configServer, ZoneRegistryMock zoneRegistry,
                             GitHubMock gitHub, CuratorDb curator, RotationsConfig rotationsConfig,
                             MemoryNameService nameService, ArtifactRepositoryMock artifactRepository,
                             EntityService entityService, MockBuildService buildService) {
        this.db = db;
        this.athenzDb = athenzDb;
        this.clock = clock;
        this.configServer = configServer;
        this.zoneRegistry = zoneRegistry;
        this.gitHub = gitHub;
        this.curator = curator;
        this.nameService = nameService;
        this.rotationsConfig = rotationsConfig;
        this.artifactRepository = artifactRepository;
        this.entityService = entityService;
        this.buildService = buildService;
        this.controller = createController(db, curator, rotationsConfig, configServer, clock, gitHub, zoneRegistry,
                                           athenzDb, nameService, artifactRepository, entityService, buildService);

        // Set the log output from the root logger to use timestamps from the manual clock ;)
        Logger.getLogger("").getHandlers()[0].setFilter(
                record -> {
                    record.setMillis(clock.millis());
                    return true;
                });
    }

    public Controller controller() { return controller; }

    public CuratorDb curator() { return curator; }

    public ManualClock clock() { return clock; }

    public AthenzDbMock athenzDb() { return athenzDb; }

    public MemoryNameService nameService() { return nameService; }

    public ZoneRegistryMock zoneRegistry() { return zoneRegistry; }

    public ConfigServerClientMock configServer() { return configServer; }

    public GitHubMock gitHub() { return gitHub; }

    public ArtifactRepositoryMock artifactRepository() { return artifactRepository; }

    public EntityService entityService() { return entityService; }

    public MockBuildService buildService() { return buildService; }

    /** Create a new controller instance. Useful to verify that controller state is rebuilt from persistence */
    public final void createNewController() {
        controller = createController(db, curator, rotationsConfig, configServer, clock, gitHub, zoneRegistry, athenzDb,
                                      nameService, artifactRepository, entityService, buildService);
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, Environment environment, long projectId, Long propertyId) {
        return createAndDeploy(tenantName, domainName, applicationName, toZone(environment), projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName,
                                       String instanceName, ZoneId zone, long projectId, Long propertyId) {
        TenantName tenant = createTenant(tenantName, domainName, propertyId);
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
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, ZoneId zone, long projectId, Long propertyId) {
        return createAndDeploy(tenantName, domainName, applicationName, "default", zone, projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public Application createAndDeploy(String tenantName, String domainName, String applicationName, Environment environment, long projectId) {
        return createAndDeploy(tenantName, domainName, applicationName, environment, projectId, null);
    }

    /** Create application from slime */
    public Application createApplication(Slime slime) {
        ApplicationSerializer serializer = new ApplicationSerializer();
        Application application = serializer.fromSlime(slime);
        try (Lock lock = controller().applications().lock(application.id())) {
            controller().applications().store(new LockedApplication(application, lock));
        }
        return application;
    }

    public ZoneId toZone(Environment environment) {
        switch (environment) {
            case dev: case test:
                return ZoneId.from(environment, RegionName.from("us-east-1"));
            case staging:
                return ZoneId.from(environment, RegionName.from("us-east-3"));
            default:
                return ZoneId.from(environment, RegionName.from("us-west-1"));
        }
    }

    public AthenzDomain createDomain(String domainName) {
        AthenzDomain domain = new AthenzDomain(domainName);
        athenzDb.addDomain(new AthenzDbMock.Domain(domain));
        return domain;
    }

    public TenantName createTenant(String tenantName, String domainName, Long propertyId) {
        TenantName name = TenantName.from(tenantName);
        Optional<Tenant> existing = controller().tenants().tenant(name);
        if (existing.isPresent()) return name;
        AthenzTenant tenant = AthenzTenant.create(name, createDomain(domainName), new Property("app1Property"),
                                                  Optional.ofNullable(propertyId)
                                                          .map(Object::toString)
                                                          .map(PropertyId::new));
        controller().tenants().create(tenant, TestIdentities.userNToken);
        assertNotNull(controller().tenants().tenant(name));
        return name;
    }

    public Application createApplication(TenantName tenant, String applicationName, String instanceName, long projectId) {
        ApplicationId applicationId = ApplicationId.from(tenant.value(), applicationName, instanceName);
        controller().applications().createApplication(applicationId, Optional.of(TestIdentities.userNToken));
        controller().applications().lockOrThrow(applicationId, lockedApplication ->
                controller().applications().store(lockedApplication.withProjectId(Optional.of(projectId))));
        return controller().applications().require(applicationId);
    }

    public void deploy(Application application, ZoneId zone) {
        deploy(application, zone, new ApplicationPackage(new byte[0]));
    }

    public void deploy(Application application, ZoneId zone, ApplicationPackage applicationPackage) {
        deploy(application, zone, applicationPackage, false);
    }

    public void deploy(Application application, ZoneId zone, ApplicationPackage applicationPackage, boolean deployCurrentVersion) {
        deploy(application, zone, Optional.of(applicationPackage), deployCurrentVersion);
    }

    public void deploy(Application application, ZoneId zone, Optional<ApplicationPackage> applicationPackage, boolean deployCurrentVersion) {
        ScrewdriverId app1ScrewdriverId = new ScrewdriverId(String.valueOf(application.deploymentJobs().projectId().get()));
        GitRevision app1RevisionId = new GitRevision(new GitRepository("repo"), new GitBranch("master"), new GitCommit("commit1"));
        controller().applications().deployApplication(application.id(),
                                                      zone,
                                                      applicationPackage,
                                                      new DeployOptions(Optional.of(new ScrewdriverBuildJob(app1ScrewdriverId, app1RevisionId)), Optional.empty(), false, deployCurrentVersion));
    }

    // Used by ApplicationSerializerTest to avoid breaking encapsulation. Should not be used by anything else
    public static LockedApplication writable(Application application) {
        return new LockedApplication(application, new Lock("/test", new MockCurator()));
    }

    private static Controller createController(ControllerDb db, CuratorDb curator, RotationsConfig rotationsConfig,
                                               ConfigServerClientMock configServerClientMock, ManualClock clock,
                                               GitHubMock gitHubClientMock, ZoneRegistryMock zoneRegistryMock,
                                               AthenzDbMock athensDb, MemoryNameService nameService,
                                               ArtifactRepository artifactRepository, EntityService entityService,
                                               BuildService buildService) {
        Controller controller = new Controller(db,
                                               curator,
                                               rotationsConfig,
                                               gitHubClientMock,
                                               entityService,
                                               new MockOrganization(clock),
                                               new MemoryGlobalRoutingService(),
                                               zoneRegistryMock,
                                               configServerClientMock,
                                               new NodeRepositoryClientMock(),
                                               new MockMetricsService(),
                                               nameService,
                                               new MockRoutingGenerator(),
                                               new ChefMock(),
                                               clock,
                                               new AthenzClientFactoryMock(athensDb),
                                               artifactRepository,
                                               buildService);
        controller.updateVersionStatus(VersionStatus.compute(controller));
        return controller;
    }

    private static RotationsConfig defaultRotationsConfig() {
        RotationsConfig.Builder builder = new RotationsConfig.Builder();
        for (int i = 1; i <= 10; i++) {
            String id = String.format("%02d", i);
            builder = builder.rotations("rotation-id-" + id, "rotation-fqdn-" + id);
        }
        return new RotationsConfig(builder);
    }

}
