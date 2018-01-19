// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Oyvind Grønnesby
 */
public interface ConfigServerClient {

    interface PreparedApplication {
        void activate();
        List<Log> messages();
        PrepareResponse prepareResponse();
    }

    PreparedApplication prepare(DeploymentId applicationInstance, DeployOptions deployOptions, Set<String> rotationCnames, Set<String> rotationNames, byte[] content);

    List<String> getNodeQueryHost(DeploymentId applicationInstance, String type) throws NoInstanceException;

    void restart(DeploymentId applicationInstance, Optional<Hostname> hostname) throws NoInstanceException;

    void deactivate(DeploymentId applicationInstance) throws NoInstanceException;

    JsonNode waitForConfigConverge(DeploymentId applicationInstance, long timeoutInSeconds);

    JsonNode grabLog(DeploymentId applicationInstance);

    ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName, String environment, String region);
    
    Map<?,?> getServiceApiResponse(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String restPath);
    
    /** Returns the version this particular config server is running */
    Version version(URI configserverUri);

    /**
     * Set new status on en endpoint in one zone.
     *
     * @param deployment The application/zone pair
     * @param endpoint The endpoint to modify
     * @param status The new status with metadata
     * @throws IOException If trouble contacting the server
     */
    void setGlobalRotationStatus(DeploymentId deployment, String endpoint, EndpointStatus status) throws IOException;

    /**
     * Get the endpoint status for an app in one zone
     *
     * @param deployment The application/zone pair
     * @param endpoint The endpoint to modify
     * @return The endpoint status with metadata
     * @throws IOException If trouble contacting the server
     */
    EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String endpoint) throws IOException;

}
