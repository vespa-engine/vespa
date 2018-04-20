// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author mortent
 */
public class ConfigServerMock extends AbstractComponent implements ConfigServer {

    private final Map<ApplicationId, Boolean> applicationActivated = new HashMap<>();
    private final Map<String, EndpointStatus> endpoints = new HashMap<>();
    private final Map<URI, Version> versions = new HashMap<>();

    private Version defaultVersion = new Version(6, 1, 0);
    private Version lastPrepareVersion = null;
    private RuntimeException prepareException = null;

    /** The version given in the previous prepare call, or empty if no call has been made */
    public Optional<Version> lastPrepareVersion() {
        return Optional.ofNullable(lastPrepareVersion);
    }

    /** Return map of applications that may have been activated */
    public Map<ApplicationId, Boolean> activated() {
        return Collections.unmodifiableMap(applicationActivated);
    }

    /** The exception to throw on the next prepare run, or null to continue normally */
    public void throwOnNextPrepare(RuntimeException prepareException) {
        this.prepareException = prepareException;
    }

    /**
     * Returns the (initially empty) mutable map of config server urls to versions.
     * This API will return defaultVersion as response to any version(url) call for versions not added to the map.
     */
    public Map<URI, Version> versions() {
        return versions;
    }

    /** Set the default config server version */
    public void setDefaultVersion(Version version) {
        this.defaultVersion = version;
    }

    public Version getDefaultVersion() {
        return defaultVersion;
    }

    @Override
    public PreparedApplication deploy(DeploymentId deployment, DeployOptions deployOptions, Set<String> rotationCnames,
                                       Set<String> rotationNames, byte[] content) {
        lastPrepareVersion = deployOptions.vespaVersion.map(Version::new).orElse(null);
        if (prepareException != null) {
            RuntimeException prepareException = this.prepareException;
            this.prepareException = null;
            throw prepareException;
        }
        applicationActivated.put(deployment.applicationId(), false);

        return new PreparedApplication() {
            @Override
            public void activate() { /* Nothing to do, done in  */}

            @Override
            public List<Log> messages() {
                Log warning = new Log();
                warning.level = "WARNING";
                warning.time  = 1;
                warning.message = "The warning";

                Log info = new Log();
                info.level = "INFO";
                info.time  = 2;
                info.message = "The info";

                return Arrays.asList(warning, info);
            }

            @Override
            public PrepareResponse prepareResponse() {
                applicationActivated.put(deployment.applicationId(), true);

                PrepareResponse prepareResponse = new PrepareResponse();
                prepareResponse.message = "foo";
                prepareResponse.configChangeActions = new ConfigChangeActions(Collections.emptyList(),
                                                                              Collections.emptyList());
                prepareResponse.tenant = new TenantId("tenant");
                return prepareResponse;
            }
        };
    }

    @Override
    public void restart(DeploymentId deployment, Optional<Hostname> hostname) {
    }

    @Override
    public void deactivate(DeploymentId deployment) {
        applicationActivated.remove(deployment.applicationId());
    }

    @Override
    public JsonNode waitForConfigConverge(DeploymentId applicationInstance, long timeoutInSeconds) {
        ObjectNode root = new ObjectNode(JsonNodeFactory.instance);
        root.put("generation", 1);
        return root;
    }

    // Returns a canned example response
    @Override
    public ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName,
                                              String environment, String region) {
        ApplicationView applicationView = new ApplicationView();
        ClusterView cluster = new ClusterView();
        cluster.name = "cluster1";
        cluster.type = "content";
        cluster.url = "http://localhost:8080/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/service/container-clustercontroller-6s8slgtps7ry8uh6lx21ejjiv/cluster/v2/cluster1";
        ServiceView service = new ServiceView();
        service.configId = "cluster1/storage/0";
        service.host = "host1";
        service.serviceName = "storagenode";
        service.serviceType = "storagenode";
        service.url = "http://localhost:8080/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/service/storagenode-awe3slno6mmq2fye191y324jl/state/v1/";
        cluster.services = new ArrayList<>();
        cluster.services.add(service);
        applicationView.clusters = new ArrayList<>();
        applicationView.clusters.add(cluster);
        return applicationView;
    }

    // Returns a canned example response
    @Override
    public Map<?,?> getServiceApiResponse(String tenantName, String applicationName, String instanceName,
                                          String environment, String region, String serviceName, String restPath) {
        Map<String,List<?>> root = new HashMap<>();
        List<Map<?,?>> resources = new ArrayList<>();
        Map<String,String> resource = new HashMap<>();
        resource.put("url", "http://localhost:8080/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/service/filedistributorservice-dud1f4w037qdxdrn0ovxfdtgw/state/v1/config");
        resources.add(resource);
        root.put("resources", resources);
        return root;
    }
    
    @Override
    public Version version(URI configServerUri) {
        return versions.getOrDefault(configServerUri, defaultVersion);
    }

    @Override
    public void setGlobalRotationStatus(DeploymentId deployment, String endpoint, EndpointStatus status) {
        endpoints.put(endpoint, status);
    }

    @Override
    public EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String endpoint) {
        EndpointStatus result = new EndpointStatus(EndpointStatus.Status.in, "", "", 1497618757L);
        return endpoints.getOrDefault(endpoint, result);
    }

}
