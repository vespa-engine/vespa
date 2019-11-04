// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMavenRepository;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.security.AthenzCredentials;
import com.yahoo.vespa.hosted.controller.security.AthenzTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.versions.ControllerVersion;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Convenience methods for controller tests.
 *
 * @author bratseth
 * @author mpolden
 */
public final class ControllerTester {

    public static final int availableRotations = 10;

    private final boolean inContainer;
    private final AthenzDbMock athenzDb;
    private final ManualClock clock;
    private final ZoneRegistryMock zoneRegistry;
    private final ServiceRegistryMock serviceRegistry;
    private final CuratorDb curator;
    private final RotationsConfig rotationsConfig;
    private final AtomicLong nextPropertyId = new AtomicLong(1000);
    private final AtomicInteger nextProjectId = new AtomicInteger(1000);
    private final AtomicInteger nextDomainId = new AtomicInteger(1000);
    private final AtomicInteger nextMinorVersion = new AtomicInteger(ControllerVersion.CURRENT.version().getMinor() + 1);

    private Controller controller;

    public ControllerTester(RotationsConfig rotationsConfig, MockCuratorDb curatorDb) {
        this(new AthenzDbMock(),
             new ZoneRegistryMock(),
             curatorDb,
             rotationsConfig,
             new ServiceRegistryMock());
    }

    public ControllerTester(RotationsConfig rotationsConfig) {
        this(rotationsConfig, new MockCuratorDb());
    }

    public ControllerTester(MockCuratorDb curatorDb) {
        this(defaultRotationsConfig(), curatorDb);
    }

    public ControllerTester() {
        this(defaultRotationsConfig(), new MockCuratorDb());
    }

    private ControllerTester(AthenzDbMock athenzDb, boolean inContainer,
                             ZoneRegistryMock zoneRegistry,
                             CuratorDb curator, RotationsConfig rotationsConfig,
                             ServiceRegistryMock serviceRegistry, Controller controller) {
        this.athenzDb = athenzDb;
        this.inContainer = inContainer;
        this.clock = serviceRegistry.clock();
        this.zoneRegistry = zoneRegistry;
        this.serviceRegistry = serviceRegistry;
        this.curator = curator;
        this.rotationsConfig = rotationsConfig;
        this.controller = controller;

        // Make root logger use time from manual clock
        configureDefaultLogHandler(handler -> handler.setFilter(
                record -> {
                    record.setInstant(clock.instant());
                    return true;
                }));
    }

    private ControllerTester(AthenzDbMock athenzDb,
                             ZoneRegistryMock zoneRegistry,
                             CuratorDb curator, RotationsConfig rotationsConfig,
                             ServiceRegistryMock serviceRegistry) {
        this(athenzDb, false, zoneRegistry, curator, rotationsConfig, serviceRegistry,
             createController(curator, rotationsConfig, zoneRegistry, athenzDb, serviceRegistry));
    }

    /** Creates a ControllerTester built on the ContainerTester's controller. This controller can not be recreated. */
    public ControllerTester(ContainerTester tester) {
        this(tester.athenzClientFactory().getSetup(),
             true,
             tester.serviceRegistry().zoneRegistryMock(),
             tester.controller().curator(),
             null,
             tester.serviceRegistry(),
             tester.controller());
    }


    public void configureDefaultLogHandler(Consumer<Handler> configureFunc) {
        Arrays.stream(Logger.getLogger("").getHandlers())
              // Do not mess with log configuration if a custom one has been set
              .filter(ignored -> System.getProperty("java.util.logging.config.file") == null)
              .forEach(configureFunc);
    }

    public static BuildService.BuildJob buildJob(ApplicationId id, JobType jobType) {
        if (jobType == JobType.component)
            throw new AssertionError("Not supposed to happen");

        return BuildService.BuildJob.of(id, 0, jobType.jobName());
    }

    public Controller controller() { return controller; }

    public CuratorDb curator() { return curator; }

    public ManualClock clock() { return clock; }

    public AthenzDbMock athenzDb() { return athenzDb; }

    public MemoryNameService nameService() { return serviceRegistry.nameServiceMock(); }

    public ZoneRegistryMock zoneRegistry() { return zoneRegistry; }

    public ConfigServerMock configServer() { return serviceRegistry.configServerMock(); }

    public ServiceRegistryMock serviceRegistry() { return serviceRegistry; }

    public Optional<Record> findCname(String name) {
        return serviceRegistry.nameService().findRecords(Record.Type.CNAME, RecordName.from(name)).stream().findFirst();
    }

    /**
     * Returns a version suitable as the next system version, i.e. a version that is always higher than the compiled-in
     * controller version.
     */
    public Version nextVersion() {
        var current = ControllerVersion.CURRENT.version();
        return new Version(current.getMajor(), nextMinorVersion.getAndIncrement(), current.getMicro());
    }

    /** Create a new controller instance. Useful to verify that controller state is rebuilt from persistence */
    public final void createNewController() {
        if (inContainer)
            throw new UnsupportedOperationException("Cannot recreate this controller");
        controller = createController(curator, rotationsConfig, zoneRegistry, athenzDb, serviceRegistry);
    }

    /** Creates the given tenant and application and deploys it */
    public void createAndDeploy(String tenantName, String domainName, String applicationName, Environment environment, long projectId, Long propertyId) {
        createAndDeploy(tenantName, domainName, applicationName, toZone(environment), projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public void createAndDeploy(String tenantName, String domainName, String applicationName,
                                    String instanceName, ZoneId zone, long projectId, Long propertyId) {
        throw new AssertionError("Not supposed to use this");
    }

    /** Creates the given tenant and application and deploys it */
    public void createAndDeploy(String tenantName, String domainName, String applicationName, ZoneId zone, long projectId, Long propertyId) {
        createAndDeploy(tenantName, domainName, applicationName, "default", zone, projectId, propertyId);
    }

    /** Creates the given tenant and application and deploys it */
    public void createAndDeploy(String tenantName, String domainName, String applicationName, Environment environment, long projectId) {
        createAndDeploy(tenantName, domainName, applicationName, environment, projectId, null);
    }

    /** Upgrade controller to given version */
    public void upgradeController(Version version, String commitSha, Instant commitDate) {
        for (var hostname : controller().curator().cluster()) {
            upgradeController(hostname, version, commitSha, commitDate);
        }
    }

    /** Upgrade controller to given version */
    public void upgradeController(HostName hostname, Version version, String commitSha, Instant commitDate) {
        controller().curator().writeControllerVersion(hostname, new ControllerVersion(version, commitSha, commitDate));
        computeVersionStatus();
    }

    public void upgradeController(Version version) {
        upgradeController(version, "badc0ffee", Instant.EPOCH);
    }

    /** Upgrade system applications in all zones to given version */
    public void upgradeSystemApplications(Version version) {
        upgradeSystemApplications(version, SystemApplication.all());
    }

    /** Upgrade given system applications in all zones to version */
    public void upgradeSystemApplications(Version version, List<SystemApplication> systemApplications) {
        for (ZoneApi zone : zoneRegistry().zones().all().zones()) {
            for (SystemApplication application : systemApplications) {
                configServer().setVersion(application.id(), zone.getId(), version);
                configServer().convergeServices(application.id(), zone.getId());
            }
        }
        computeVersionStatus();
    }

    /** Upgrade entire system to given version */
    public void upgradeSystem(Version version) {
        upgradeController(version);
        upgradeSystemApplications(version);
    }

    /** Re-compute and write version status */
    public void computeVersionStatus() {
        controller().updateVersionStatus(VersionStatus.compute(controller()));
    }

    public int hourOfDayAfter(Duration duration) {
        clock().advance(duration);
        return controller().clock().instant().atOffset(ZoneOffset.UTC).getHour();
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
        athenzDb.getOrCreateDomain(domain).admin(user);
        return domain;
    }

    public Optional<AthenzDomain> domainOf(TenantAndApplicationId id) {
        Tenant tenant = controller().tenants().require(id.tenant());
        return tenant.type() == Tenant.Type.athenz ? Optional.of(((AthenzTenant) tenant).domain()) : Optional.empty();
    }

    public TenantName createTenant(String tenantName, String domainName, Long propertyId, Optional<Contact> contact) {
        TenantName name = TenantName.from(tenantName);
        Optional<Tenant> existing = controller().tenants().get(name);
        if (existing.isPresent()) return name;
        AthenzUser user = new AthenzUser("user");
        AthenzDomain domain = createDomainWithAdmin(domainName, user);
        AthenzTenantSpec tenantSpec = new AthenzTenantSpec(name,
                                                           domain,
                                                           new Property("Property" + propertyId),
                                                           Optional.ofNullable(propertyId).map(Object::toString).map(PropertyId::new));
        AthenzCredentials credentials = new AthenzCredentials(
                new AthenzPrincipal(user), domain, new OktaIdentityToken("okta-identity-token"), new OktaAccessToken("okta-access-token"));
        controller().tenants().create(tenantSpec, credentials);
        if (contact.isPresent())
            controller().tenants().lockOrThrow(name, LockedTenant.Athenz.class, tenant ->
                    controller().tenants().store(tenant.with(contact.get())));
        assertNotNull(controller().tenants().get(name));
        return name;
    }

    public TenantName createTenant(String tenantName) {
        return createTenant(tenantName, "domain" + nextDomainId.getAndIncrement(),
                            nextPropertyId.getAndIncrement());
    }

    public TenantName createTenant(String tenantName, String domainName, Long propertyId) {
        return createTenant(tenantName, domainName, propertyId, Optional.empty());
    }

    public Optional<Credentials> credentialsFor(TenantAndApplicationId id) {
        return domainOf(id).map(domain -> new AthenzCredentials(new AthenzPrincipal(new AthenzUser("user")),
                                                                domain,
                                                                new OktaIdentityToken("okta-identity-token"),
                                                                new OktaAccessToken("okta-access-token")));
    }

    public Application createApplication(TenantName tenant, String applicationName, String instanceName) {
        return createApplication(tenant, applicationName, instanceName, nextProjectId.getAndIncrement());
    }

    public Application createApplication(TenantName tenant, String applicationName, String instanceName, long projectId) {
        TenantAndApplicationId applicationId = TenantAndApplicationId.from(tenant.value(), applicationName);
        controller().applications().createApplication(applicationId, credentialsFor(applicationId));
        controller().applications().lockApplicationOrThrow(applicationId, application ->
                controller().applications().store(application.withProjectId(OptionalLong.of(projectId))));
        controller().applications().createInstance(applicationId.instance(instanceName));
        Application application = controller().applications().requireApplication(applicationId);
        assertTrue(application.projectId().isPresent());
        return application;
    }

    public void deploy(ApplicationId id, ZoneId zone) {
        deploy(id, zone, new ApplicationPackage(new byte[0]));
    }

    public void deploy(ApplicationId id, ZoneId zone, ApplicationPackage applicationPackage) {
        deploy(id, zone, applicationPackage, false);
    }

    public void deploy(ApplicationId id, ZoneId zone, ApplicationPackage applicationPackage, boolean deployCurrentVersion) {
        deploy(id, zone, Optional.of(applicationPackage), deployCurrentVersion);
    }

    public void deploy(ApplicationId id, ZoneId zone, Optional<ApplicationPackage> applicationPackage, boolean deployCurrentVersion) {
        deploy(id, zone, applicationPackage, deployCurrentVersion, Optional.empty());
    }

    public void deploy(ApplicationId id, ZoneId zone, Optional<ApplicationPackage> applicationPackage, boolean deployCurrentVersion, Optional<Version> version) {
        controller().applications().deploy(id,
                                           zone,
                                           applicationPackage,
                                           new DeployOptions(false, version, false, deployCurrentVersion));
    }

    public Supplier<Instance> application(ApplicationId application) {
        return () -> controller().applications().requireInstance(application);
    }

    private static Controller createController(CuratorDb curator, RotationsConfig rotationsConfig,
                                               ZoneRegistryMock zoneRegistryMock,
                                               AthenzDbMock athensDb,
                                               ServiceRegistryMock serviceRegistry) {
        Controller controller = new Controller(curator,
                                               rotationsConfig,
                                               zoneRegistryMock,
                                               new AthenzFacade(new AthenzClientFactoryMock(athensDb)),
                                               () -> "test-controller",
                                               new InMemoryFlagSource(),
                                               new MockMavenRepository(),
                                               serviceRegistry);
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
