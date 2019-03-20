// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Slime;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService;
import com.yahoo.vespa.hosted.controller.api.integration.github.GitHubMock;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockContactRetriever;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockIssueHandler;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockRunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.integration.ApplicationStoreMock;
import com.yahoo.vespa.hosted.controller.integration.ArtifactRepositoryMock;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.MetricsServiceMock;
import com.yahoo.vespa.hosted.controller.integration.RoutingGeneratorMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.permits.ApplicationPermit;
import com.yahoo.vespa.hosted.controller.permits.AthenzApplicationPermit;
import com.yahoo.vespa.hosted.controller.permits.AthenzTenantPermit;
import com.yahoo.vespa.hosted.controller.persistence.ApplicationSerializer;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

/**
 * Convenience methods for controller tests.
 *
 * @author bratseth
 * @author mpolden
 */
public final class ControllerTester {

    public static final int availableRotations = 10;

    private final AthenzDbMock athenzDb;
    private final ManualClock clock;
    private final ConfigServerMock configServer;
    private final ZoneRegistryMock zoneRegistry;
    private final GitHubMock gitHub;
    private final CuratorDb curator;
    private final MemoryNameService nameService;
    private final RotationsConfig rotationsConfig;
    private final ArtifactRepositoryMock artifactRepository;
    private final ApplicationStoreMock applicationStore;
    private final EntityService entityService;
    private final MockBuildService buildService;
    private final MetricsServiceMock metricsService;
    private final RoutingGeneratorMock routingGenerator;
    private final MockContactRetriever contactRetriever;
    private final MockIssueHandler issueHandler;

    private Controller controller;

    public ControllerTester(ManualClock clock, RotationsConfig rotationsConfig, MockCuratorDb curatorDb,
                            MetricsServiceMock metricsService) {
        this(new AthenzDbMock(), clock, new ConfigServerMock(new ZoneRegistryMock()),
             new ZoneRegistryMock(), new GitHubMock(), curatorDb, rotationsConfig,
             new MemoryNameService(), new ArtifactRepositoryMock(), new ApplicationStoreMock(),
             new MemoryEntityService(), new MockBuildService(),
             metricsService, new RoutingGeneratorMock(), new MockContactRetriever(), new MockIssueHandler(clock));
    }

    public ControllerTester(ManualClock clock) {
        this(clock, defaultRotationsConfig(), new MockCuratorDb(), new MetricsServiceMock());
    }

    public ControllerTester(RotationsConfig rotationsConfig) {
        this(new ManualClock(), rotationsConfig, new MockCuratorDb(), new MetricsServiceMock());
    }

    public ControllerTester(MockCuratorDb curatorDb) {
        this(new ManualClock(), defaultRotationsConfig(), curatorDb, new MetricsServiceMock());
    }

    public ControllerTester() {
        this(new ManualClock());
    }

    private ControllerTester(AthenzDbMock athenzDb, ManualClock clock,
                             ConfigServerMock configServer, ZoneRegistryMock zoneRegistry,
                             GitHubMock gitHub, CuratorDb curator, RotationsConfig rotationsConfig,
                             MemoryNameService nameService, ArtifactRepositoryMock artifactRepository,
                             ApplicationStoreMock appStoreMock,
                             EntityService entityService, MockBuildService buildService,
                             MetricsServiceMock metricsService, RoutingGeneratorMock routingGenerator,
                             MockContactRetriever contactRetriever, MockIssueHandler issueHandler) {
        this.athenzDb = athenzDb;
        this.clock = clock;
        this.configServer = configServer;
        this.zoneRegistry = zoneRegistry;
        this.gitHub = gitHub;
        this.curator = curator;
        this.nameService = nameService;
        this.rotationsConfig = rotationsConfig;
        this.artifactRepository = artifactRepository;
        this.applicationStore = appStoreMock;
        this.entityService = entityService;
        this.buildService = buildService;
        this.metricsService = metricsService;
        this.routingGenerator = routingGenerator;
        this.contactRetriever = contactRetriever;
        this.issueHandler = issueHandler;
        this.controller = createController(curator, rotationsConfig, configServer, clock, gitHub, zoneRegistry,
                                           athenzDb, nameService, artifactRepository, appStoreMock, entityService, buildService,
                                           metricsService, routingGenerator);

        // Make root logger use time from manual clock
        configureDefaultLogHandler(handler -> handler.setFilter(
                record -> {
                    record.setInstant(clock.instant());
                    return true;
                }));
    }

    public void configureDefaultLogHandler(Consumer<Handler> configureFunc) {
        Arrays.stream(Logger.getLogger("").getHandlers())
              // Do not mess with log configuration if a custom one has been set
              .filter(ignored -> System.getProperty("java.util.logging.config.file") == null)
              .findFirst()
              .ifPresent(configureFunc);
    }

    public static BuildService.BuildJob buildJob(Application application, JobType jobType) {
        return BuildService.BuildJob.of(application.id(), application.deploymentJobs().projectId().getAsLong(), jobType.jobName());
    }

    public Controller controller() { return controller; }

    public CuratorDb curator() { return curator; }

    public ManualClock clock() { return clock; }

    public AthenzDbMock athenzDb() { return athenzDb; }

    public MemoryNameService nameService() { return nameService; }

    public ZoneRegistryMock zoneRegistry() { return zoneRegistry; }

    public ConfigServerMock configServer() { return configServer; }

    public ArtifactRepositoryMock artifactRepository() { return artifactRepository; }

    public ApplicationStoreMock applicationStore() { return applicationStore; }

    public MockBuildService buildService() { return buildService; }

    public MetricsServiceMock metricsService() { return metricsService; }

    public RoutingGeneratorMock routingGenerator() { return routingGenerator; }

    public MockContactRetriever contactRetriever() {
        return contactRetriever;
    }

    /** Create a new controller instance. Useful to verify that controller state is rebuilt from persistence */
    public final void createNewController() {
        controller = createController(curator, rotationsConfig, configServer, clock, gitHub, zoneRegistry, athenzDb,
                                      nameService, artifactRepository, applicationStore, entityService, buildService, metricsService,
                                      routingGenerator);
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

    public AthenzDomain createDomainWithAdmin(String domainName, AthenzUser user) {
        AthenzDomain domain = new AthenzDomain(domainName);
        athenzDb.addDomain(new AthenzDbMock.Domain(domain));
        athenzDb.domains.get(domain).admin(user);
        return domain;
    }

    public Optional<AthenzDomain> domainOf(ApplicationId id) {
        Tenant tenant = controller().tenants().require(id.tenant());
        return tenant.type() == Tenant.Type.athenz ? Optional.of(((AthenzTenant) tenant).domain()) : Optional.empty();
    }

    public TenantName createTenant(String tenantName, String domainName, Long propertyId, Optional<Contact> contact) {
        TenantName name = TenantName.from(tenantName);
        Optional<Tenant> existing = controller().tenants().get(name);
        if (existing.isPresent()) return name;
        AthenzUser user = new AthenzUser("user");
        AthenzTenantPermit permit = new AthenzTenantPermit(name,
                                                           new AthenzPrincipal(user),
                                                           Optional.of(createDomainWithAdmin(domainName, user)),
                                                           Optional.of(new Property("Property" + propertyId)),
                                                           Optional.ofNullable(propertyId).map(Object::toString).map(PropertyId::new),
                                                           new OktaAccessToken("okta-token"));
        controller().tenants().create(permit);
        if (contact.isPresent())
            controller().tenants().lockOrThrow(name, LockedTenant.Athenz.class, tenant ->
                    controller().tenants().store(tenant.with(contact.get())));
        assertNotNull(controller().tenants().get(name));
        return name;
    }

    public TenantName createTenant(String tenantName, String domainName, Long propertyId) {
        return createTenant(tenantName, domainName, propertyId, Optional.empty());
    }

    public Optional<ApplicationPermit> permitFor(ApplicationId id) {
        return domainOf(id).map(domain -> new AthenzApplicationPermit(id, domain, new OktaAccessToken("okta-token")));
    }

    public Application createApplication(TenantName tenant, String applicationName, String instanceName, long projectId) {
        ApplicationId applicationId = ApplicationId.from(tenant.value(), applicationName, instanceName);
        controller().applications().createApplication(applicationId, permitFor(applicationId));
        controller().applications().lockOrThrow(applicationId, lockedApplication ->
                controller().applications().store(lockedApplication.withProjectId(OptionalLong.of(projectId))));
        return controller().applications().require(applicationId);
    }

    public void deleteApplication(ApplicationId id) {
        controller().applications().deleteApplication(id, permitFor(id));
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
        controller().applications().deploy(application.id(),
                                           zone,
                                           applicationPackage,
                                           new DeployOptions(false, Optional.empty(), false, deployCurrentVersion));
    }

    public Supplier<Application> application(ApplicationId application) {
        return () -> controller().applications().require(application);
    }

    /** Used by ApplicationSerializerTest to avoid breaking encapsulation. Should not be used by anything else */
    public static LockedApplication writable(Application application) {
        return new LockedApplication(application, new Lock("/test", new MockCurator()));
    }

    private static Controller createController(CuratorDb curator, RotationsConfig rotationsConfig,
                                               ConfigServerMock configServer, ManualClock clock,
                                               GitHubMock gitHub, ZoneRegistryMock zoneRegistryMock,
                                               AthenzDbMock athensDb, MemoryNameService nameService,
                                               ArtifactRepository artifactRepository, ApplicationStore applicationStore,
                                               EntityService entityService,
                                               BuildService buildService, MetricsServiceMock metricsService,
                                               RoutingGenerator routingGenerator) {
        Controller controller = new Controller(curator,
                                               rotationsConfig,
                                               gitHub,
                                               entityService,
                                               zoneRegistryMock,
                                               configServer,
                                               metricsService,
                                               nameService,
                                               routingGenerator,
                                               new ChefMock(),
                                               clock,
                                               new AthenzFacade(new AthenzClientFactoryMock(athensDb)),
                                               artifactRepository,
                                               applicationStore,
                                               new MockTesterCloud(),
                                               buildService,
                                               new MockRunDataStore(),
                                               () -> "test-controller",
                                               new MockMailer());
        // Calculate initial versions
        controller.updateVersionStatus(VersionStatus.compute(controller));
        return controller;
    }

    private static RotationsConfig defaultRotationsConfig() {
        RotationsConfig.Builder builder = new RotationsConfig.Builder();
        for (int i = 1; i <= availableRotations; i++) {
            String id = String.format("%02d", i);
            builder = builder.rotations("rotation-id-" + id, "rotation-fqdn-" + id);
        }
        return new RotationsConfig(builder);
    }

}
