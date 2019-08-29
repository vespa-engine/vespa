// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.MemoryNameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.MemoryGlobalRoutingService;

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

    @Override
    public ConfigServer configServer() {
        return configServerMock;
    }

    @Override
    public GlobalRoutingService globalRoutingService() {
        return memoryGlobalRoutingService;
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
}
