// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.api.identifiers.ControllerVersion;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.archive.MockArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AccessControlService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.MockAccessControlService;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockEnclaveAccessService;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockResourceTagger;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockRoleService;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClientMock;
import com.yahoo.vespa.hosted.controller.api.integration.billing.MockBillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidator;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidatorMock;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MockVpcEndpointService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService;
import com.yahoo.vespa.hosted.controller.api.integration.horizon.HorizonClient;
import com.yahoo.vespa.hosted.controller.api.integration.horizon.MockHorizonClient;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockContactRetriever;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockIssueHandler;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostReportConsumerMock;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClientMock;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.EndpointSecretManager;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.GcpSecretStore;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.NoopEndpointSecretManager;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.NoopGcpSecretStore;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.NoopTenantSecretService;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.DummyOwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.DummySystemMonitor;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockRunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.user.RoleMaintainer;
import com.yahoo.vespa.hosted.controller.api.integration.user.RoleMaintainerMock;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.MockChangeRequestClient;

import java.time.Instant;
import java.util.Optional;

/**
 * A mock implementation of a {@link ServiceRegistry} for testing purposes.
 *
 * @author mpolden
 */
public class ServiceRegistryMock extends AbstractComponent implements ServiceRegistry {

    private final ManualClock clock = new ManualClock();
    private final ControllerVersion controllerVersion;
    private final ZoneRegistryMock zoneRegistryMock;
    private final ConfigServerMock configServerMock;
    private final MemoryNameService memoryNameService = new MemoryNameService();
    private final MockVpcEndpointService vpcEndpointService = new MockVpcEndpointService(clock, memoryNameService);
    private final MockMailer mockMailer = new MockMailer();
    private final EndpointCertificateMock endpointCertificateMock = new EndpointCertificateMock();
    private final EndpointCertificateValidatorMock endpointCertificateValidatorMock = new EndpointCertificateValidatorMock();
    private final MockContactRetriever mockContactRetriever = new MockContactRetriever();
    private final MockIssueHandler mockIssueHandler = new MockIssueHandler();
    private final DummyOwnershipIssues dummyOwnershipIssues = new DummyOwnershipIssues();
    private final LoggingDeploymentIssues loggingDeploymentIssues = new LoggingDeploymentIssues();
    private final MemoryEntityService memoryEntityService = new MemoryEntityService();
    private final DummySystemMonitor systemMonitor = new DummySystemMonitor();
    private final CostReportConsumerMock costReportConsumerMock = new CostReportConsumerMock();
    private final ArtifactRepositoryMock artifactRepositoryMock = new ArtifactRepositoryMock();
    private final MockTesterCloud mockTesterCloud;
    private final ApplicationStoreMock applicationStoreMock = new ApplicationStoreMock();
    private final MockRunDataStore mockRunDataStore = new MockRunDataStore();
    private final MockEnclaveAccessService mockAMIService = new MockEnclaveAccessService();
    private final MockResourceTagger mockResourceTagger = new MockResourceTagger();
    private final MockRoleService roleService = new MockRoleService();
    private final MockBillingController billingController = new MockBillingController(clock);
    private final ArtifactRegistryMock containerRegistry = new ArtifactRegistryMock();
    private final NoopTenantSecretService tenantSecretService = new NoopTenantSecretService();
    private final NoopEndpointSecretManager secretManager = new NoopEndpointSecretManager();
    private final ArchiveService archiveService = new MockArchiveService(clock);
    private final MockChangeRequestClient changeRequestClient = new MockChangeRequestClient();
    private final AccessControlService accessControlService = new MockAccessControlService();
    private final HorizonClient horizonClient = new MockHorizonClient();
    private final PlanRegistry planRegistry = new PlanRegistryMock();
    private final ResourceDatabaseClient resourceDb = new ResourceDatabaseClientMock(planRegistry);
    private final BillingDatabaseClient billingDb = new BillingDatabaseClientMock(clock, planRegistry);
    private final RoleMaintainerMock roleMaintainer = new RoleMaintainerMock();

    public ServiceRegistryMock(SystemName system) {
        this.zoneRegistryMock = new ZoneRegistryMock(system);
        this.configServerMock = new ConfigServerMock(zoneRegistryMock, memoryNameService);
        this.mockTesterCloud = new MockTesterCloud(memoryNameService);
        this.clock.setInstant(Instant.ofEpochSecond(1600000000));
        this.controllerVersion = new ControllerVersion(Version.fromString("6.1.0"), "badb01", clock.instant());
    }

    @Inject
    public ServiceRegistryMock(ConfigserverConfig config) {
        this(SystemName.from(config.system()));
    }

    public ServiceRegistryMock() {
        this(SystemName.main);
    }

    @Override
    public ConfigServerMock configServer() {
        return configServerMock;
    }

    @Override
    public ManualClock clock() {
        return clock;
    }

    @Override
    public ControllerVersion controllerVersion() {
        return controllerVersion;
    }

    @Override
    public HostName getHostname() {
        return HostName.of("test-controller");
    }

    @Override
    public MockMailer mailer() {
        return mockMailer;
    }

    @Override
    public EndpointCertificateMock endpointCertificateProvider() {
        return endpointCertificateMock;
    }

    @Override
    public EndpointCertificateValidator endpointCertificateValidator() {
        return endpointCertificateValidatorMock;
    }

    @Override
    public MockContactRetriever contactRetriever() {
        return mockContactRetriever;
    }

    @Override
    public MockIssueHandler issueHandler() {
        return mockIssueHandler;
    }

    @Override
    public DummyOwnershipIssues ownershipIssues() {
        return dummyOwnershipIssues;
    }

    @Override
    public LoggingDeploymentIssues deploymentIssues() {
        return loggingDeploymentIssues;
    }

    @Override
    public MemoryEntityService entityService() {
        return memoryEntityService;
    }

    @Override
    public CostReportConsumerMock costReportConsumer() {
        return costReportConsumerMock;
    }

    @Override
    public ArtifactRepositoryMock artifactRepository() {
        return artifactRepositoryMock;
    }

    @Override
    public MockTesterCloud testerCloud() {
        return mockTesterCloud;
    }

    @Override
    public ApplicationStoreMock applicationStore() {
        return applicationStoreMock;
    }

    @Override
    public MockRunDataStore runDataStore() {
        return mockRunDataStore;
    }

    @Override
    public MemoryNameService nameService() {
        return memoryNameService;
    }

    @Override
    public MockVpcEndpointService vpcEndpointService() {
        return vpcEndpointService;
    }

    @Override
    public ZoneRegistryMock zoneRegistry() {
        return zoneRegistryMock;
    }

    @Override
    public MockResourceTagger resourceTagger() {
        return mockResourceTagger;
    }

    @Override
    public MockEnclaveAccessService enclaveAccessService() {
        return mockAMIService;
    }

    @Override
    public MockRoleService roleService() {
        return roleService;
    }

    @Override
    public DummySystemMonitor systemMonitor() {
        return systemMonitor;
    }

    @Override
    public BillingController billingController() {
        return billingController;
    }

    @Override
    public Optional<ArtifactRegistryMock> artifactRegistry(CloudName cloudName) {
        return Optional.of(containerRegistry);
    }

    @Override
    public NoopTenantSecretService tenantSecretService() {
        return tenantSecretService;
    }

    @Override
    public EndpointSecretManager secretManager() {
        return secretManager;
    }

    @Override
    public ArchiveService archiveService() {
        return archiveService;
    }

    @Override
    public MockChangeRequestClient changeRequestClient() {
        return changeRequestClient;
    }

    @Override
    public AccessControlService accessControlService() {
        return accessControlService;
    }

    @Override
    public HorizonClient horizonClient() {
        return horizonClient;
    }

    @Override
    public PlanRegistry planRegistry() {
        return planRegistry;
    }

    @Override
    public ResourceDatabaseClient resourceDatabase() {
        return resourceDb;
    }

    @Override
    public BillingDatabaseClient billingDatabase() {
        return billingDb;
    }

    @Override
    public RoleMaintainer roleMaintainer() {
        return roleMaintainer;
    }

    public ConfigServerMock configServerMock() {
        return configServerMock;
    }

    public MockContactRetriever contactRetrieverMock() {
        return mockContactRetriever;
    }

    public EndpointCertificateMock endpointCertificateMock() {
        return endpointCertificateMock;
    }

    public RoleMaintainerMock roleMaintainerMock() {
        return roleMaintainer;
    }

    public GcpSecretStore gcpSecretStore() { return new NoopGcpSecretStore(); }
}
