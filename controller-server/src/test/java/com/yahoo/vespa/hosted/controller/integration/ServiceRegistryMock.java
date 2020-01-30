// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.SystemName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockAwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockResourceTagger;
import com.yahoo.vespa.hosted.controller.api.integration.aws.ResourceTagger;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.MemoryEntityService;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockBilling;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockContactRetriever;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockIssueHandler;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostReportConsumerMock;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MockTenantCost;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGeneratorMock;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.DummyOwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.LoggingDeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMeteringClient;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockRunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;

/**
 * A mock implementation of a {@link ServiceRegistry} for testing purposes.
 *
 * @author mpolden
 */
public class ServiceRegistryMock extends AbstractComponent implements ServiceRegistry {

    private final ManualClock clock = new ManualClock();
    private final ZoneRegistryMock zoneRegistryMock;
    private final ConfigServerMock configServerMock;
    private final MemoryNameService memoryNameService = new MemoryNameService();
    private final MemoryGlobalRoutingService memoryGlobalRoutingService = new MemoryGlobalRoutingService();
    private final RoutingGeneratorMock routingGeneratorMock;
    private final MockMailer mockMailer = new MockMailer();
    private final ApplicationCertificateMock applicationCertificateMock = new ApplicationCertificateMock();
    private final MockMeteringClient mockMeteringClient = new MockMeteringClient();
    private final MockContactRetriever mockContactRetriever = new MockContactRetriever();
    private final MockIssueHandler mockIssueHandler = new MockIssueHandler();
    private final DummyOwnershipIssues dummyOwnershipIssues = new DummyOwnershipIssues();
    private final LoggingDeploymentIssues loggingDeploymentIssues = new LoggingDeploymentIssues();
    private final MemoryEntityService memoryEntityService = new MemoryEntityService();
    private final CostReportConsumerMock costReportConsumerMock = new CostReportConsumerMock();
    private final MockBilling mockBilling = new MockBilling();
    private final MockAwsEventFetcher mockAwsEventFetcher = new MockAwsEventFetcher();
    private final ArtifactRepositoryMock artifactRepositoryMock = new ArtifactRepositoryMock();
    private final MockTesterCloud mockTesterCloud = new MockTesterCloud();
    private final ApplicationStoreMock applicationStoreMock = new ApplicationStoreMock();
    private final MockRunDataStore mockRunDataStore = new MockRunDataStore();
    private final MockTenantCost mockTenantCost = new MockTenantCost();
    private final MockResourceTagger mockResourceTagger = new MockResourceTagger();

    public ServiceRegistryMock(SystemName system) {
        this.zoneRegistryMock = new ZoneRegistryMock(system);
        this.configServerMock = new ConfigServerMock(zoneRegistryMock);
        this.routingGeneratorMock = new RoutingGeneratorMock(RoutingGeneratorMock.TEST_ENDPOINTS, zoneRegistryMock);
    }

    @Inject
    public ServiceRegistryMock(ConfigserverConfig config) {
        this(SystemName.from(config.system()));
    }

    public ServiceRegistryMock() {
        this(SystemName.main);
    }

    @Override
    public ConfigServer configServer() {
        return configServerMock;
    }

    @Override
    public ManualClock clock() {
        return clock;
    }

    @Override
    public GlobalRoutingService globalRoutingService() {
        return memoryGlobalRoutingService;
    }

    @Override
    public RoutingGenerator routingGenerator() {
        return routingGeneratorMock;
    }

    @Override
    public MockMailer mailer() {
        return mockMailer;
    }

    @Override
    public ApplicationCertificateMock applicationCertificateProvider() {
        return applicationCertificateMock;
    }

    @Override
    public MockMeteringClient meteringService() {
        return mockMeteringClient;
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
    public MockBilling billingService() {
        return mockBilling;
    }

    @Override
    public MockAwsEventFetcher eventFetcherService() {
        return mockAwsEventFetcher;
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
    public MockTenantCost tenantCost() { return mockTenantCost;}

    @Override
    public ZoneRegistryMock zoneRegistry() {
        return zoneRegistryMock;
    }

    @Override
    public ResourceTagger resourceTagger() {
        return mockResourceTagger;
    }

    public ConfigServerMock configServerMock() {
        return configServerMock;
    }

    public MemoryNameService nameServiceMock() {
        return memoryNameService;
    }

    public MemoryGlobalRoutingService globalRoutingServiceMock() {
        return memoryGlobalRoutingService;
    }

    public RoutingGeneratorMock routingGeneratorMock() {
        return routingGeneratorMock;
    }

    public MockContactRetriever contactRetrieverMock() {
        return mockContactRetriever;
    }

    public ArtifactRepositoryMock artifactRepositoryMock() {
        return artifactRepositoryMock;
    }

    public ApplicationCertificateMock applicationCertificateMock() {
        return applicationCertificateMock;
    }

}
