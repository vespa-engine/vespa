// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.test.ManualClock;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OAuthCredentials;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMavenRepository;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockUserManagement;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.security.AthenzCredentials;
import com.yahoo.vespa.hosted.controller.security.AthenzTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Auth0Credentials;
import com.yahoo.vespa.hosted.controller.security.CloudAccessControl;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.security.TenantSpec;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.ControllerVersion;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.yolean.concurrent.Sleeper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private final ServiceRegistryMock serviceRegistry;
    private final CuratorDb curator;
    private final RotationsConfig rotationsConfig;
    private final InMemoryFlagSource flagSource;
    private final AtomicLong nextPropertyId = new AtomicLong(1000);
    private final AtomicInteger nextProjectId = new AtomicInteger(1000);
    private final AtomicInteger nextDomainId = new AtomicInteger(1000);
    private final AtomicInteger nextMinorVersion = new AtomicInteger(ControllerVersion.CURRENT.version().getMinor() + 1);

    private Controller controller;

    public ControllerTester(RotationsConfig rotationsConfig, MockCuratorDb curatorDb) {
        this(new AthenzDbMock(),
             curatorDb,
             rotationsConfig,
             new ServiceRegistryMock());
    }

    public ControllerTester(ServiceRegistryMock serviceRegistryMock) {
        this(new AthenzDbMock(), new MockCuratorDb(serviceRegistryMock.zoneRegistry().system()), defaultRotationsConfig(), serviceRegistryMock);
    }

    public ControllerTester(RotationsConfig rotationsConfig, SystemName system) {
        this(new AthenzDbMock(), new MockCuratorDb(system), rotationsConfig, new ServiceRegistryMock(system));
    }

    public ControllerTester(MockCuratorDb curatorDb) {
        this(defaultRotationsConfig(), curatorDb);
    }

    public ControllerTester() {
        this(defaultRotationsConfig(), new MockCuratorDb(new ServiceRegistryMock().zoneRegistry().system()));
    }

    public ControllerTester(SystemName system) {
        this(new AthenzDbMock(), new MockCuratorDb(system), defaultRotationsConfig(), new ServiceRegistryMock(system));
    }

    private ControllerTester(AthenzDbMock athenzDb, boolean inContainer, CuratorDb curator,
                             RotationsConfig rotationsConfig, ServiceRegistryMock serviceRegistry,
                             InMemoryFlagSource flagSource, Controller controller) {
        this.athenzDb = athenzDb;
        this.inContainer = inContainer;
        this.clock = serviceRegistry.clock();
        this.serviceRegistry = serviceRegistry;
        this.curator = curator;
        this.rotationsConfig = rotationsConfig;
        this.flagSource = flagSource.withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true)
                                    .withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of(), String.class);
        this.controller = controller;

        // Make root logger use time from manual clock
        configureDefaultLogHandler(handler -> handler.setFilter(
                record -> {
                    record.setInstant(clock.instant());
                    return true;
                }));
    }

    private ControllerTester(AthenzDbMock athenzDb,
                             CuratorDb curator, RotationsConfig rotationsConfig,
                             ServiceRegistryMock serviceRegistry) {
        this(athenzDb, curator, rotationsConfig, serviceRegistry, new InMemoryFlagSource());
    }

    private ControllerTester(AthenzDbMock athenzDb,
                             CuratorDb curator, RotationsConfig rotationsConfig,
                             ServiceRegistryMock serviceRegistry, InMemoryFlagSource flagSource) {
        this(athenzDb, false, curator, rotationsConfig, serviceRegistry, flagSource,
             createController(curator, rotationsConfig, athenzDb, serviceRegistry, flagSource));
    }

    /** Creates a ControllerTester built on the ContainerTester's controller. This controller can not be recreated. */
    public ControllerTester(ContainerTester tester) {
        this(tester.athenzClientFactory().getSetup(),
             true,
             tester.controller().curator(),
             null,
             tester.serviceRegistry(),
             tester.flagSource(),
             tester.controller());
    }


    public void configureDefaultLogHandler(Consumer<Handler> configureFunc) {
        Arrays.stream(Logger.getLogger("").getHandlers())
              // Do not mess with log configuration if a custom one has been set
              .filter(ignored -> System.getProperty("java.util.logging.config.file") == null)
              .forEach(configureFunc);
    }

    public Controller controller() { return controller; }

    public CuratorDb curator() { return curator; }

    public ManualClock clock() { return clock; }

    public AthenzDbMock athenzDb() { return athenzDb; }

    public InMemoryFlagSource flagSource() { return flagSource; }

    public MemoryNameService nameService() {
        return serviceRegistry.nameService();
    }

    public ZoneRegistryMock zoneRegistry() { return serviceRegistry.zoneRegistry(); }

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

    /** Set the zones and system for this and bootstrap infrastructure nodes */
    public ControllerTester setZones(List<ZoneId> zones) {
        ZoneApiMock.Builder builder = ZoneApiMock.newBuilder().withSystem(zoneRegistry().system());
        zoneRegistry().setZones(zones.stream().map(zone -> builder.with(zone).build()).collect(Collectors.toList()));
        configServer().bootstrap(zones, SystemApplication.notController());
        return this;
    }

    /** Set the routing method for given zones */
    public ControllerTester setRoutingMethod(List<ZoneId> zones, RoutingMethod routingMethod) {
        zoneRegistry().setRoutingMethod(zones.stream().map(ZoneApiMock::from).collect(Collectors.toList()),
                                        routingMethod);
        return this;
    }

    /** Create a new controller instance. Useful to verify that controller state is rebuilt from persistence */
    public void createNewController() {
        if (inContainer)
            throw new UnsupportedOperationException("Cannot recreate this controller");
        controller = createController(curator, rotationsConfig, athenzDb, serviceRegistry, flagSource);
    }

    /** Upgrade controller to given version */
    public void upgradeController(Version version, String commitSha, Instant commitDate) {
        for (var hostname : controller().curator().cluster()) {
            upgradeController(HostName.of(hostname), version, commitSha, commitDate);
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
        upgradeSystemApplications(version, SystemApplication.notController());
    }

    /** Upgrade given system applications in all zones to version */
    public void upgradeSystemApplications(Version version, List<SystemApplication> systemApplications) {
        for (ZoneApi zone : zoneRegistry().zones().all().zones()) {
            for (SystemApplication application : systemApplications) {
                if (!application.hasApplicationPackage()) {
                    configServer().nodeRepository().upgrade(zone.getId(), application.nodeType(), version, false);
                }
                configServer().setVersion(version, application.id(), zone.getId());
                configServer().convergeServices(application.id(), zone.getId());
            }
        }
        computeVersionStatus();
    }

    /** Upgrade entire system to given version */
    public void upgradeSystem(Version version) {
        ((MockMavenRepository) controller.mavenRepository()).addVersion(version);
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

    public TenantName createTenant(String tenantName) {
        return createTenant(tenantName, zoneRegistry().system().isPublic() ? Tenant.Type.cloud : Tenant.Type.athenz);
    }

    public TenantName createTenant(String tenantName, Tenant.Type type) {
        switch (type) {
            case athenz: return createTenant(tenantName, "domain" + nextDomainId.getAndIncrement(), nextPropertyId.getAndIncrement());
            case cloud: return createCloudTenant(tenantName);
            default: throw new UnsupportedOperationException();
        }
    }

    public TenantName createTenant(String tenantName, String domainName, Long propertyId) {
        return createAthenzTenant(tenantName, domainName, propertyId, Optional.empty());
    }

    private TenantName createAthenzTenant(String tenantName, String domainName, Long propertyId, Optional<Contact> contact) {
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
                new AthenzPrincipal(user), domain, OAuthCredentials.createForTesting("okta-access-token", "okta-identity-token"));
        controller().tenants().create(tenantSpec, credentials);
        contact.ifPresent(value -> controller().tenants().lockOrThrow(name, LockedTenant.Athenz.class, tenant ->
                controller().tenants().store(tenant.with(value))));
        assertNotNull(controller().tenants().get(name));
        return name;
    }

    private TenantName createCloudTenant(String tenantName) {
        TenantName tenant = TenantName.from(tenantName);
        TenantSpec spec = new CloudTenantSpec(tenant, "token");
        controller().tenants().create(spec, new Auth0Credentials(new SimplePrincipal("dev-" + tenantName), Set.of(Role.administrator(tenant))));
        return tenant;
    }

    public Credentials credentialsFor(TenantName tenantName) {
        Tenant tenant = controller().tenants().require(tenantName);

        switch (tenant.type()) {
            case athenz:
                return new AthenzCredentials(new AthenzPrincipal(new AthenzUser("user")),
                                                                             ((AthenzTenant) tenant).domain(),
                                                                             OAuthCredentials.createForTesting("okta-access-token", "okta-identity-token"));
            case cloud:
                return new Credentials(new SimplePrincipal("dev"));

            default:
                throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'");
        }
    }

    public Application createApplication(ApplicationId id) {
        return createApplication(id.tenant().value(), id.application().value(), id.instance().value());
    }

    public Application createApplication(String tenant, String applicationName, String instanceName) {
        Application application = createApplication(tenant, applicationName);
        controller().applications().createInstance(application.id().instance(instanceName), Tags.empty());
        return application;
    }

    public Application createApplication(String tenant, String applicationName) {
        TenantAndApplicationId applicationId = TenantAndApplicationId.from(tenant, applicationName);
        controller().applications().getApplication(applicationId)
                .orElseGet(() -> controller().applications().createApplication(applicationId, credentialsFor(applicationId.tenant())));
        controller().applications().lockApplicationOrThrow(applicationId, app ->
                controller().applications().store(app.withProjectId(OptionalLong.of(nextProjectId.getAndIncrement()))));
        Application application = controller().applications().requireApplication(applicationId);
        assertTrue(application.projectId().isPresent());
        return application;
    }

    private static Controller createController(CuratorDb curator, RotationsConfig rotationsConfig,
                                               AthenzDbMock athensDb,
                                               ServiceRegistryMock serviceRegistry,
                                               FlagSource flagSource) {
        Controller controller = new Controller(curator,
                                               rotationsConfig,
                                               serviceRegistry.zoneRegistry().system().isPublic() ?
                                                       new CloudAccessControl(new MockUserManagement(), flagSource, serviceRegistry) :
                                                       new AthenzFacade(new AthenzClientFactoryMock(athensDb)),
                                               flagSource,
                                               new MockMavenRepository(),
                                               serviceRegistry,
                                               new MetricsMock(), new SecretStoreMock(),
                                               new ControllerConfig.Builder().build(),
                                               Sleeper.NOOP);
        // Calculate initial versions
        controller.updateVersionStatus(VersionStatus.compute(controller));
        return controller;
    }

    private static RotationsConfig defaultRotationsConfig() {
        RotationsConfig.Builder builder = new RotationsConfig.Builder();
        for (int i = 1; i <= availableRotations; i++) {
            String id = Text.format("%02d", i);
            builder = builder.rotations("rotation-id-" + id, "rotation-fqdn-" + id);
        }
        return new RotationsConfig(builder);
    }

}
