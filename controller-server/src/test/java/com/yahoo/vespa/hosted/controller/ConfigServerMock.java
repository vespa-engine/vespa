// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author mortent
 */
public class ConfigServerMock extends AbstractComponent implements ConfigServer {

    private final Map<ApplicationId, Application> applications = new LinkedHashMap<>();
    private final Map<String, EndpointStatus> endpoints = new HashMap<>();
    private final Map<URI, Version> versions = new HashMap<>();
    private final NodeRepositoryMock nodeRepository = new NodeRepositoryMock();

    private Version initialVersion = new Version(6, 1, 0);
    private Version lastPrepareVersion = null;
    private RuntimeException prepareException = null;

    @Inject
    public ConfigServerMock(ZoneRegistryMock zoneRegistry) {
        bootstrap(zoneRegistry.zones().all().ids(), SystemApplication.all());
    }

    public void bootstrap(List<ZoneId> zones, SystemApplication... applications) {
        bootstrap(zones, Arrays.asList(applications));
    }

    public void bootstrap(List<ZoneId> zones, List<SystemApplication> applications) {
        nodeRepository().clear();
        for (ZoneId zone : zones) {
            for (SystemApplication application : applications) {
                List<Node> nodes = IntStream.rangeClosed(1, 3)
                                            .mapToObj(i -> new Node(
                                                    HostName.from("node-" + i + "-" + application.id().application()
                                                                                                 .value()),
                                                    Node.State.active, application.nodeType(),
                                                    Optional.of(application.id()),
                                                    initialVersion,
                                                    initialVersion
                                            ))
                                            .collect(Collectors.toList());
                nodeRepository().add(zone, nodes);
            }
        }
    }

    /** The version given in the previous prepare call, or empty if no call has been made */
    public Optional<Version> lastPrepareVersion() {
        return Optional.ofNullable(lastPrepareVersion);
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

    /** Set version for system applications in given zone */
    public void setVersion(Version version, ZoneId zone, List<SystemApplication> applications) {
        for (SystemApplication application : applications) {
            for (Node node : nodeRepository().list(zone, application.id())) {
                nodeRepository().add(zone, new Node(node.hostname(), node.state(), node.type(), node.owner(),
                                                    version, version));
            }
        }
    }

    /** The initial version of this config server */
    public Version initialVersion() {
        return initialVersion;
    }

    /** Get deployed application by ID */
    public Optional<Application> application(ApplicationId id) {
        return Optional.ofNullable(applications.get(id));
    }

    @Override
    public NodeRepositoryMock nodeRepository() {
        return nodeRepository;
    }

    @Override
    public PreparedApplication deploy(DeploymentId deployment, DeployOptions deployOptions, Set<String> rotationCnames,
                                       Set<String> rotationNames, byte[] content) {
        lastPrepareVersion = deployOptions.vespaVersion.map(Version::fromString).orElse(null);
        if (prepareException != null) {
            RuntimeException prepareException = this.prepareException;
            this.prepareException = null;
            throw prepareException;
        }
        applications.put(deployment.applicationId(), new Application(deployment.applicationId(), lastPrepareVersion));

        return new PreparedApplication() {

            @Override
            public void activate() {}

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
                Application application = applications.get(deployment.applicationId());
                application.activate();
                for (Node node : nodeRepository.list(deployment.zoneId(), deployment.applicationId())) {
                    nodeRepository.add(deployment.zoneId(), new Node(node.hostname(),
                                                                     node.state(), node.type(),
                                                                     node.owner(),
                                                                     node.currentVersion(),
                                                                     application.version().get()));
                }

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
        applications.remove(deployment.applicationId());
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
    public void setGlobalRotationStatus(DeploymentId deployment, String endpoint, EndpointStatus status) {
        endpoints.put(endpoint, status);
    }

    @Override
    public EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String endpoint) {
        EndpointStatus result = new EndpointStatus(EndpointStatus.Status.in, "", "", 1497618757L);
        return endpoints.getOrDefault(endpoint, result);
    }

    public static class Application {

        private final ApplicationId id;
        private final Version version;
        private boolean activated;

        private Application(ApplicationId id, Version version) {
            this.id = id;
            this.version = version;
        }

        public ApplicationId id() {
            return id;
        }

        public Optional<Version> version() {
            return Optional.ofNullable(version);
        }

        public boolean activated() {
            return activated;
        }

        private void activate() {
            this.activated = true;
        }

    }

}
