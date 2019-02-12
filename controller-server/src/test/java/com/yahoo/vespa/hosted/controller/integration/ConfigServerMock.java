// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.Identifier;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Logs;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author mortent
 * @author jonmv
 */
public class ConfigServerMock extends AbstractComponent implements ConfigServer {

    private final Map<ApplicationId, Application> applications = new LinkedHashMap<>();
    private final Map<String, EndpointStatus> endpoints = new HashMap<>();
    private final NodeRepositoryMock nodeRepository = new NodeRepositoryMock();
    private final Map<DeploymentId, ServiceConvergence> serviceStatus = new HashMap<>();
    private final Version initialVersion = new Version(6, 1, 0);
    private final Set<DeploymentId> suspendedApplications = new HashSet<>();
    private final Map<ZoneId, List<LoadBalancer>> loadBalancers = new HashMap<>();

    private Version lastPrepareVersion = null;
    private RuntimeException prepareException = null;
    private ConfigChangeActions configChangeActions = null;

    @Inject
    public ConfigServerMock(ZoneRegistryMock zoneRegistry) {
        bootstrap(zoneRegistry.zones().all().ids(), SystemApplication.all(), Optional.empty());
    }

    /** Sets the ConfigChangeActions that will be returned on next deployment. */
    public void setConfigChangeActions(ConfigChangeActions configChangeActions) {
        this.configChangeActions = configChangeActions;
    }

    /** Assigns a reserved tenant node to the given deployment, with initial versions. */
    public void provision(ZoneId zone, ApplicationId application) {
        nodeRepository().putByHostname(zone, new Node(hostFor(application, zone),
                                                      Node.State.reserved,
                                                      NodeType.tenant,
                                                      Optional.of(application),
                                                      initialVersion,
                                                      initialVersion));
    }

    public HostName hostFor(ApplicationId application, ZoneId zone) {
        return HostName.from("host-" + application.serializedForm() + "-" + zone.value());
    }

    public void bootstrap(List<ZoneId> zones, SystemApplication... applications) {
        bootstrap(zones, List.of(applications), Optional.empty());
    }

    public void bootstrap(List<ZoneId> zones, List<SystemApplication> applications, Optional<NodeType> type) {
        nodeRepository().clear();
        addNodes(zones, applications, type);
    }

    public void addNodes(List<ZoneId> zones, List<SystemApplication> applications, Optional<NodeType> type) {
        for (ZoneId zone : zones) {
            for (SystemApplication application : applications) {
                List<Node> nodes = IntStream.rangeClosed(1, 3)
                                            .mapToObj(i -> new Node(
                                                    HostName.from("node-" + i + "-" + application.id().application()
                                                                                                 .value()),
                                                    Node.State.active, type.orElseGet(() -> application.nodeTypes().iterator().next()),
                                                    Optional.of(application.id()),
                                                    initialVersion,
                                                    initialVersion
                                            ))
                                            .collect(Collectors.toList());
                nodeRepository().putByHostname(zone, nodes);
                convergeServices(application.id(), zone);
            }
        }
    }

    /** Converge all services belonging to the given application */
    public void convergeServices(ApplicationId application, ZoneId zone) {
        List<Node> nodes = nodeRepository.list(zone, application);
        serviceStatus.put(new DeploymentId(application, zone), new ServiceConvergence(application,
                                                                                      zone,
                                                                                      true,
                                                                                      2,
                                                                                      nodes.stream()
                                                                                           .map(node -> new ServiceConvergence.Status(node.hostname(),
                                                                                                                                      43,
                                                                                                                                      "container",
                                                                                                                                      2))
                                                                                           .collect(Collectors.toList())));
    }

    /** The version given in the previous prepare call, or empty if no call has been made */
    public Optional<Version> lastPrepareVersion() {
        return Optional.ofNullable(lastPrepareVersion);
    }

    /** The exception to throw on the next prepare run, or null to continue normally */
    public void throwOnNextPrepare(RuntimeException prepareException) {
        this.prepareException = prepareException;
    }

    /** Set version for an application in a given zone */
    public void setVersion(ApplicationId application, ZoneId zone, Version version) {
        for (Node node : nodeRepository().list(zone, application)) {
            nodeRepository().putByHostname(zone, new Node(node.hostname(), node.state(), node.type(), node.owner(),
                                                          version, version));
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

    public void setSuspended(DeploymentId deployment, boolean suspend) {
        if (suspend)
            suspendedApplications.add(deployment);
        else
            suspendedApplications.remove(deployment);
    }

    @Override
    public NodeRepositoryMock nodeRepository() {
        return nodeRepository;
    }

    @Override
    public Optional<ServiceConvergence> serviceConvergence(DeploymentId deployment) {
        return Optional.ofNullable(serviceStatus.get(deployment));
    }

    @Override
    public List<LoadBalancer> getLoadBalancers(ZoneId zone) {
        return loadBalancers.getOrDefault(zone, Collections.emptyList());
    }

    public void addLoadBalancers(ZoneId zone, List<LoadBalancer> loadBalancers) {
        this.loadBalancers.compute(zone, (k, existing) -> {
           if (existing == null) {
               existing = new ArrayList<>();
           }
           existing.addAll(loadBalancers);
           return existing;
        });
    }

    public void removeLoadBalancers(ApplicationId application, ZoneId zone) {
        getLoadBalancers(zone).removeIf(lb -> lb.application().equals(application));
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
        applications.put(deployment.applicationId(), new Application(deployment.applicationId(), lastPrepareVersion, new ApplicationPackage(content)));

        if (nodeRepository().list(deployment.zoneId(), deployment.applicationId()).isEmpty())
            provision(deployment.zoneId(), deployment.applicationId());

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

                return List.of(warning, info);
            }

            @Override
            public PrepareResponse prepareResponse() {
                Application application = applications.get(deployment.applicationId());
                application.activate();
                List<Node> nodes = nodeRepository.list(deployment.zoneId(), deployment.applicationId());
                for (Node node : nodes) {
                    nodeRepository.putByHostname(deployment.zoneId(), new Node(node.hostname(),
                                                                               node.state(), node.type(),
                                                                               node.owner(),
                                                                               node.currentVersion(),
                                                                               application.version().get()));
                }
                serviceStatus.put(deployment, new ServiceConvergence(deployment.applicationId(),
                                                                     deployment.zoneId(),
                                                                     false,
                                                                     2,
                                                                     nodes.stream()
                                                                          .map(node -> new ServiceConvergence.Status(node.hostname(),
                                                                                                                     43,
                                                                                                                     "container",
                                                                                                                     1))
                                                                          .collect(Collectors.toList())));

                PrepareResponse prepareResponse = new PrepareResponse();
                prepareResponse.message = "foo";
                prepareResponse.configChangeActions = configChangeActions != null
                        ? configChangeActions
                        : new ConfigChangeActions(Collections.emptyList(),
                                                  Collections.emptyList());
                setConfigChangeActions(null);
                prepareResponse.tenant = new TenantId("tenant");
                prepareResponse.log = Collections.emptyList();
                return prepareResponse;
            }

        };
    }

    @Override
    public boolean isSuspended(DeploymentId deployment) {
        return suspendedApplications.contains(deployment);
    }

    @Override
    public void restart(DeploymentId deployment, Optional<Hostname> hostname) {
        nodeRepository().requestRestart(deployment, hostname.map(Identifier::id).map(HostName::from));
    }

    @Override
    public void deactivate(DeploymentId deployment) {
        applications.remove(deployment.applicationId());
        nodeRepository().removeByHostname(deployment.zoneId(),
                                          nodeRepository().list(deployment.zoneId(), deployment.applicationId()));
        serviceStatus.remove(deployment);
    }

    // Returns a canned example response
    @Override
    public ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName,
                                              String environment, String region) {
        String cfgHostname = String.format("https://cfg.%s.%s.test.vip:4443", environment, region);
        String cfgServiceUrlPrefix = String.format("%s/serviceview/v1/tenant/%s/application/%s/environment/%s/region/%s/instance/%s/service",
                                                   cfgHostname, tenantName, applicationName,
                                                   environment, region, instanceName);
        ApplicationView applicationView = new ApplicationView();
        ClusterView cluster = new ClusterView();
        cluster.name = "cluster1";
        cluster.type = "content";
        cluster.url = cfgServiceUrlPrefix + "/container-clustercontroller-6s8slgtps7ry8uh6lx21ejjiv/cluster/v2/cluster1";
        ServiceView service = new ServiceView();
        service.configId = "cluster1/storage/0";
        service.host = "host1";
        service.serviceName = "storagenode";
        service.serviceType = "storagenode";
        service.url = cfgServiceUrlPrefix + "/storagenode-awe3slno6mmq2fye191y324jl/state/v1/";
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

    @Override
    public Optional<Logs> getLogs(DeploymentId deployment, HashMap<String, String> queryParameters) {
        HashMap<String, String> logs = new HashMap<>();
        logs.put("subfolder-log2.log", "VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxl");
        logs.put("log1.log", "VGhpcyBpcyBvbmUgbG9nIGZpbGU=");
        return Optional.of(new Logs(logs));
    }

    @Override
    public List<String> getContentClusters(DeploymentId deployment) {
        return Collections.singletonList("music");
    }

    public static class Application {

        private final ApplicationId id;
        private final Version version;
        private boolean activated;
        private final ApplicationPackage applicationPackage;

        private Application(ApplicationId id, Version version, ApplicationPackage applicationPackage) {
            this.id = id;
            this.version = version;
            this.applicationPackage = applicationPackage;
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

        public ApplicationPackage applicationPackage() {
            return applicationPackage;
        }

        private void activate() {
            this.activated = true;
        }

    }

}
