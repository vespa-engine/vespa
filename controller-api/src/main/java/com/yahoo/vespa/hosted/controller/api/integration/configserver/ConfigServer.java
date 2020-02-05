// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.io.InputStream;
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
        PrepareResponse prepareResponse();
    }

    PreparedApplication deploy(DeploymentId deployment, DeployOptions deployOptions,
                               Set<ContainerEndpoint> containerEndpoints, Optional<EndpointCertificateMetadata> endpointCertificateMetadata,
                               byte[] content);

    void restart(DeploymentId deployment, Optional<Hostname> hostname);

    void deactivate(DeploymentId deployment) throws NotFoundException;

    boolean isSuspended(DeploymentId deployment);

    ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName, String environment, String region);

    Map<?,?> getServiceApiResponse(DeploymentId deployment, String serviceName, String restPath);

    String getClusterControllerStatus(DeploymentId deployment, String restPath);

    /**
     * Gets the Vespa logs of the given deployment.
     *
     * If the "from" and/or "to" query parameters are present, they are read as millis since EPOCH, and used
     * to limit the time window for which log entries are gathered. <em>This is not exact, and will return too much.</em>
     * If the "hostname" query parameter is present, it limits the entries to be from that host.
     */
    InputStream getLogs(DeploymentId deployment, Map<String, String> queryParameters);

    List<ClusterMetrics> getMetrics(DeploymentId deployment);

    List<String> getContentClusters(DeploymentId deployment);

    /**
     * Set new status for a endpoint of a single deployment.
     *
     * @param deployment   The deployment to change
     * @param upstreamName The upstream to modify. Upstream name is a unique identifier for the global route of a
     *                     deployment in the shared routing layer
     * @param status       The new status
     */
    void setGlobalRotationStatus(DeploymentId deployment, String upstreamName, EndpointStatus status);

    /**
     * Set the new status for an entire zone.
     *
     * @param zone the zone
     * @param in whether to set zone status to 'in' or 'out'
     */
    void setGlobalRotationStatus(ZoneId zone, boolean in);

    /**
     * Get the endpoint status for an app in one zone.
     *
     * @param deployment   The deployment to change
     * @param upstreamName The upstream to query. Upstream name is a unique identifier for the global route of a
     *                     deployment in the shared routing layer
     * @return The endpoint status with metadata
     */
    EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String upstreamName);

    /**
     * Get the status for an entire zone.
     *
     * @param zone the zone
     * @return whether the zone status is 'in'
     */
    boolean getGlobalRotationStatus(ZoneId zone);

    /** The node repository on this config server */
    NodeRepository nodeRepository();

    /** Get service convergence status for given deployment, using the nodes in the model at the given Vespa version. */
    Optional<ServiceConvergence> serviceConvergence(DeploymentId deployment, Optional<Version> version);

    /** Get all load balancers for application in given zone */
    List<LoadBalancer> getLoadBalancers(ApplicationId application, ZoneId zone);

    /** List all flag data for the given zone */
    List<FlagData> listFlagData(ZoneId zone);

    /** Gets status for tester application */
    TesterCloud.Status getTesterStatus(DeploymentId deployment);

    /** Starts tests on tester node */
    String startTests(DeploymentId deployment, TesterCloud.Suite suite, byte[] config);

    /** Gets log from tester node */
    List<LogEntry> getTesterLog(DeploymentId deployment, long after);

    /** Is tester node ready */
    boolean isTesterReady(DeploymentId deployment);

}
