// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;

/**
 * A mock implementation of a {@link ServiceRegistry} for testing purposes.
 *
 * @author mpolden
 */
public class ServiceRegistryMock extends AbstractComponent implements ServiceRegistry {

    private final ZoneRegistryMock zoneRegistryMock = new ZoneRegistryMock();
    private final ConfigServerMock configServerMock = new ConfigServerMock(zoneRegistryMock);
    private final MemoryNameService memoryNameService = new MemoryNameService();
    private final MemoryGlobalRoutingService memoryGlobalRoutingService = new MemoryGlobalRoutingService();
    private final RoutingGeneratorMock routingGeneratorMock = new RoutingGeneratorMock();
    private final MockMailer mockMailer = new MockMailer();
    private final ApplicationCertificateMock applicationCertificateMock = new ApplicationCertificateMock();

    @Override
    public ConfigServer configServer() {
        return configServerMock;
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
    public Mailer mailer() {
        return mockMailer;
    }

    @Override
    public ApplicationCertificateProvider applicationCertificateProvider() {
        return applicationCertificateMock;
    }

    @Override
    public NameService nameService() {
        return memoryNameService;
    }

    public ZoneRegistryMock zoneRegistryMock() {
        return zoneRegistryMock;
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

}
