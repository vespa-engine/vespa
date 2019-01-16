// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The API controllers use when communicating with config servers.
 *
 * @author Oyvind Grønnesby
 */
public interface ConfigServer {

    interface PreparedApplication {
        void activate();
        List<Log> messages();
        PrepareResponse prepareResponse();
    }

    PreparedApplication deploy(DeploymentId deployment, DeployOptions deployOptions, Set<String> rotationCnames, Set<String> rotationNames, byte[] content);

    void restart(DeploymentId deployment, Optional<Hostname> hostname);

    void deactivate(DeploymentId deployment) throws NoInstanceException;

    boolean isSuspended(DeploymentId deployment);

    ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName, String environment, String region);

    Map<?,?> getServiceApiResponse(String tenantName, String applicationName, String instanceName, String environment, String region, String serviceName, String restPath);

    Optional<Logs> getLogs(DeploymentId deployment, HashMap<String, String> queryParameters);

    List<String> getContentClusters(DeploymentId deployment);

    /**
     * Set new status on en endpoint in one zone.
     *
     * @param deployment The application/zone pair
     * @param endpoint The endpoint to modify
     * @param status The new status with metadata
     * @throws IOException If trouble contacting the server
     */
    // TODO: Remove checked exception from signature
    void setGlobalRotationStatus(DeploymentId deployment, String endpoint, EndpointStatus status) throws IOException;

    /**
     * Get the endpoint status for an app in one zone
     *
     * @param deployment The application/zone pair
     * @param endpoint The endpoint to modify
     * @return The endpoint status with metadata
     * @throws IOException If trouble contacting the server
     */
    // TODO: Remove checked exception from signature
    EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String endpoint) throws IOException;

    /** The node repository on this config server */
    NodeRepository nodeRepository();

    /** Get service convergence status for given deployment */
    Optional<ServiceConvergence> serviceConvergence(DeploymentId deployment);

    List<LoadBalancer> getLoadBalancers(DeploymentId deployment);

}
