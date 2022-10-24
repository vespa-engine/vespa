// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import ai.vespa.http.HttpURL.Query;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL.Path;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeploymentData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ProtonMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.RestartFilter;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The API controllers use when communicating with config servers.
 *
 * @author Øyvind Grønnesby
 */
public interface ConfigServer {

    interface PreparedApplication {
        DeploymentResult deploymentResult();
    }

    PreparedApplication deploy(DeploymentData deployment);

    void reindex(DeploymentId deployment, List<String> clusterNames, List<String> documentTypes, boolean indexedOnly, Double speed);

    ApplicationReindexing getReindexing(DeploymentId deployment);

    void disableReindexing(DeploymentId deployment);

    void enableReindexing(DeploymentId deployment);

    void restart(DeploymentId deployment, RestartFilter restartFilter);

    void deactivate(DeploymentId deployment);

    boolean isSuspended(DeploymentId deployment);

    /** Returns a proxied response from a given path running on a given service and node */
    ProxyResponse getServiceNodePage(DeploymentId deployment, String serviceName, DomainName node, Path subPath, Query query);

    /** Returns health status for the services of an application */
    ProxyResponse getServiceNodes(DeploymentId deployment);

    /**
     * Gets the Vespa logs of the given deployment.
     *
     * If the "from" and/or "to" query parameters are present, they are read as millis since EPOCH, and used
     * to limit the time window for which log entries are gathered. <em>This is not exact, and will return too much.</em>
     * If the "hostname" query parameter is present, it limits the entries to be from that host.
     */
    InputStream getLogs(DeploymentId deployment, Map<String, String> queryParameters);

    /**
     * Gets the contents of a file inside the current application package for a given deployment. If the path is to
     * a directly, a JSON list with URLs to contents is returned.
     *
     * @param deployment deployment to get application package content for
     * @param path path within package to get
     * @param requestUri request URI on the controller, used to rewrite paths in response from config server
     */
    ProxyResponse getApplicationPackageContent(DeploymentId deployment, Path path, URI requestUri);

    List<ClusterMetrics> getDeploymentMetrics(DeploymentId deployment);

    List<ProtonMetrics> getProtonMetrics(DeploymentId deployment);

    List<String> getContentClusters(DeploymentId deployment);

    /**
     * Set new status for a endpoint of a single deployment.
     *
     * @param deployment    The deployment to change
     * @param upstreamNames The upstream names to modify. Upstream name is a unique identifier for the routing status
     *                      of a cluster in a deployment
     * @param status        The new status
     */
    void setGlobalRotationStatus(DeploymentId deployment, List<String> upstreamNames, EndpointStatus status);

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

    Optional<TestReport> getTestReport(DeploymentId deployment);

    /** Get maximum resources consumed */
    QuotaUsage getQuotaUsage(DeploymentId deploymentId);

    /** Sets suspension status — whether application node operations are orchestrated — for the given deployment. */
    void setSuspension(DeploymentId deploymentId, boolean suspend);

    /** Validates secret store configuration. */
    String validateSecretStore(DeploymentId deploymentId, TenantSecretStore tenantSecretStore, String region, String parameterName);

}
